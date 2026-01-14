package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.EngineType;
import com.magentamause.cosybackend.engine.config.EngineProperties;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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

    public GameServerService(
            EngineManager engineManager,
            GameEntityService gameEntityService,
            EngineProperties engineProperties,
            GameServerRepository gameServerRepository) {

        this.engineManager = engineManager;
        this.gameEntityService = gameEntityService;
        this.engineType = engineProperties.selected();
        this.gameServerRepository = gameServerRepository;

        log.info("GameServerService initialized with engine '{}'", engineType);
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

    public GameServerEntity updateGameServerConfiguration(
            String uuid, GameServerUpdateDto dto) {

        GameServerEntity gameServer = gameServerRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game server with uuid " + uuid + " not found"));

        GameEntity game = gameEntityService.getGameFromUuid(dto.getGameUuid())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game with uuid " + dto.getGameUuid() + " not found"));
        gameServer.setGame(game);

        gameServer.setServerName(dto.getServerName());
        gameServer.setDockerImageName(dto.getDockerImageName());
        gameServer.setDockerImageTag(dto.getDockerImageTag());
        gameServer.setDockerExecutionCommand(dto.getExecutionCommand());

        gameServer.setPortMappings(updateList(gameServer.getPortMappings(), dto.getPortMappings(), ArrayList::new));
        gameServer.setEnvironmentVariables(updateList(gameServer.getEnvironmentVariables(), dto.getEnvironmentVariables(), ArrayList::new));
        gameServer.setVolumeMounts(updateList(
                gameServer.getVolumeMounts(),
                dto.getVolumeMounts() != null
                        ? dto.getVolumeMounts().stream()
                        .map(VolumeMountConfiguration::fromDto)
                        .toList()
                        : null,
                ArrayList::new
        ));

        return gameServerRepository.save(gameServer);
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

            return engineManager.start(config);
        } finally {
            startingServers.remove(serviceName);
        }
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
        Optional<GameEntity> game = (gameEntityService.getGameFromUuid(dto.getGameUuid()));

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
    private <T> List<T> updateList(List<T> target, List<T> source, Supplier<List<T>> listSupplier) {
        if (target == null) {
            target = listSupplier.get();
        } else {
            target.clear();
        }
        if (source != null) {
            target.addAll(source);
        }
        return target;
    }
}
