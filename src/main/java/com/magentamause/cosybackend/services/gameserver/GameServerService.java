package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.EngineType;
import com.magentamause.cosybackend.services.engine.config.EngineProperties;
import com.magentamause.cosybackend.websockets.GameServerLogWebsocketPublisher;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class GameServerService {

    private final GameServerRepository gameServerRepository;
    private final GameEntityService gameEntityService;
    private final EngineManager engineManager;
    private final EngineType engineType;
    private final Set<String> startingServers = ConcurrentHashMap.newKeySet();
    private final GameServerLogWebsocketPublisher gameServerLogWebsocketPublisher;
    private final GameServerLogService gameServerLogService;

    public GameServerService(
            EngineManager engineManager,
            GameEntityService gameEntityService,
            EngineProperties engineProperties,
            GameServerRepository gameServerRepository,
            GameServerLogWebsocketPublisher gameServerLogWebsocketPublisher,
            GameServerLogService gameServerLogService) {

        this.engineManager = engineManager;
        this.gameEntityService = gameEntityService;
        this.engineType = engineProperties.selected();
        this.gameServerRepository = gameServerRepository;
        this.gameServerLogWebsocketPublisher = gameServerLogWebsocketPublisher;

        log.info("GameServerService initialized with engine '{}'", engineType);
        this.gameServerLogService = gameServerLogService;
    }

    public List<GameServerEntity> getAllGameServers() {
        return gameServerRepository.findAll();
    }

    public GameServerEntity getGameServerById(String uuid) {
        return gameServerRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
    }

    public GameServerEntity saveGameServer(GameServerEntity entity) {
        entity.setUuid(null);
        entity.setStatus(GameServerEntity.GameServerStatus.STOPPED);
        log.info("Saving game server {}", entity);
        return gameServerRepository.save(entity);
    }

    public void deleteGameServerById(String uuid) {
        gameServerRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
        gameServerRepository.deleteById(uuid);
    }

    public GameServerEntity updateGameServerConfiguration(String uuid, GameServerEntity entity) {
        gameServerRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
        entity.setUuid(uuid);
        return gameServerRepository.save(entity);
    }

    @Transactional
    public List<Integer> startServer(String serviceName) {
        if (!startingServers.add(serviceName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Server '" + serviceName + "' is already starting");
        }

        try {
            GameServerEntity config =
                    gameServerRepository
                            .findById(serviceName)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Server '" + serviceName + "' not found"));

            enrichAndPublishLogMessage(
                    config,
                    GameServerLogMessageEntity.of(
                            config.getUuid(),
                            "Starting Game Server",
                            GameServerLogMessageEntity.LogLevel.TRACE
                    )
            );

            return engineManager.startAndAttachLogListener(
                    config,
                    (logMessage) -> {
                        enrichAndPublishLogMessage(config, logMessage);
                    });
        } finally {
            startingServers.remove(serviceName);
        }
    }

    public GameServerLogMessageEntity enrichAndPublishLogMessage(
            GameServerEntity gameServer, GameServerLogMessageEntity logMessage) {

        logMessage.setGameServerUuid(gameServer.getUuid());
        gameServerLogService.saveGameServerLog(logMessage);

        gameServerLogWebsocketPublisher.publishLog(gameServer.getUuid(), logMessage);
        return logMessage;
    }

    public void stopServer(String serviceName) {
        GameServerEntity config =
                gameServerRepository
                        .findById(serviceName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Server '" + serviceName + "' not found"));
        enrichAndPublishLogMessage(
                config,
                GameServerLogMessageEntity.of(
                        config.getUuid(),
                        "Stopping Game Server",
                        GameServerLogMessageEntity.LogLevel.TRACE
                )
        );
        try {
            engineManager.stop(config);
        } catch (ServerAlreadyStoppedException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    public GameServerStatusDto getStatus(String serviceName) {
        return engineManager.status(
                gameServerRepository
                        .findById(serviceName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Server '" + serviceName + "' not found")));
    }

    public GameServerEntity convertDtoToEntity(GameServerCreationDto dto) {
        Optional<GameEntity> game = gameEntityService.getGameFromUuid(dto.getGameUuid());

        return GameServerEntity.builder()
                .game(game.orElse(null))
                .serverName(dto.getServerName())
                .template(dto.getTemplate())
                .dockerImageName(dto.getDockerImageName())
                .dockerImageTag(dto.getDockerImageTag())
                .dockerExecutionCommand(dto.getExecutionCommand())
                .environmentVariables(dto.getEnvironmentVariables())
                .volumeMounts(
                        dto.getVolumeMounts() != null
                                ? dto.getVolumeMounts().stream()
                                .map(VolumeMountConfiguration::fromDto)
                                .toList()
                                : List.of())
                .portMappings(dto.getPortMappings() != null ? dto.getPortMappings() : List.of())
                .build();
    }
}
