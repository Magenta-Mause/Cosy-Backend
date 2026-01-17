package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.websockets.GameServerDockerProgressPublisher;
import com.magentamause.cosybackend.websockets.GameServerLogWebsocketPublisher;
import com.magentamause.cosybackend.websockets.GameServerStatusPublisher;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerService {

    private final GameServerRepository gameServerRepository;
    private final GameEntityService gameEntityService;
    private final EngineManager engineManager;
    private final Set<String> startingServers = ConcurrentHashMap.newKeySet();
    private final GameServerLogWebsocketPublisher gameServerLogWebsocketPublisher;
    private final GameServerStatusPublisher statusPublisher;
    private final GameServerDockerProgressPublisher dockerProgressPublisher;
    private final TransactionTemplate transactionTemplate;
    private final GameServerLogService gameServerLogService;

    @PostConstruct
    public void init() {
        gameServerRepository
                .findAll()
                .forEach(
                        server ->
                                engineManager.attachStatusListener(
                                        server,
                                        () -> getStatus(server.getUuid()),
                                        (status) -> updateStatus(server, status)));
        engineManager.attachStartListener(this::handleGameServerEngineStartEvent);
        engineManager.attachStopListener(this::handleGameServerEngineStopEvent);
        engineManager.attachFailListener(this::handleGameServerEngineFailEvent);
    }

    private void handleGameServerEngineStartEvent(String gameServerUuid) {
        Optional<GameServerEntity> server = gameServerRepository.findById(gameServerUuid);
        server.ifPresent(
                gameServerEntity -> {
                    enrichAndPublishLogMessage(
                            gameServerEntity,
                            GameServerLogMessageEntity.of(
                                    gameServerEntity.getUuid(),
                                    "Docker game server start event received",
                                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG));
                    String exposedPorts =
                            gameServerEntity.getPortMappings().stream()
                                    .map(PortMapping::getInstancePort)
                                    .map(Object::toString)
                                    .collect(Collectors.joining(", "));
                    enrichAndPublishLogMessage(
                            gameServerEntity,
                            GameServerLogMessageEntity.of(
                                    gameServerEntity.getUuid(),
                                    "Exposed ports: " + exposedPorts,
                                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG));
                });
    }

    private void handleGameServerEngineStopEvent(String gameServerUuid) {
        Optional<GameServerEntity> server = gameServerRepository.findById(gameServerUuid);
        server.ifPresent(
                gameServerEntity ->
                        enrichAndPublishLogMessage(
                                gameServerEntity,
                                GameServerLogMessageEntity.of(
                                        gameServerEntity.getUuid(),
                                        "Docker game server stop event received",
                                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG)));
    }

    private void handleGameServerEngineFailEvent(String gameServerUuid) {
        Optional<GameServerEntity> server = gameServerRepository.findById(gameServerUuid);
        server.ifPresent(
                gameServerEntity ->
                        enrichAndPublishLogMessage(
                                gameServerEntity,
                                GameServerLogMessageEntity.of(
                                        gameServerEntity.getUuid(),
                                        "Docker game server failure event received",
                                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG)));
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
        entity.setStatus(GameServerDto.GameServerStatus.STOPPED);
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

    public GameServerEntity updateGameServerConfiguration(String uuid, GameServerUpdateDto dto) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(uuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid " + uuid + " not found"));

        GameEntity game =
                dto.getGameUuid() == null
                        ? null
                        : gameEntityService
                                .getGameFromUuid(dto.getGameUuid())
                                .orElseThrow(
                                        () ->
                                                new ResponseStatusException(
                                                        HttpStatus.NOT_FOUND,
                                                        "Game with uuid "
                                                                + dto.getGameUuid()
                                                                + " not found"));
        gameServer.setGame(game);

        gameServer.setServerName(dto.getServerName());
        gameServer.setDockerImageName(dto.getDockerImageName());
        gameServer.setDockerImageTag(dto.getDockerImageTag());
        gameServer.setDockerExecutionCommand(dto.getExecutionCommand());

        gameServer.setPortMappings(
                updateList(gameServer.getPortMappings(), dto.getPortMappings(), ArrayList::new));
        gameServer.setEnvironmentVariables(
                updateList(
                        gameServer.getEnvironmentVariables(),
                        dto.getEnvironmentVariables(),
                        ArrayList::new));
        gameServer.setVolumeMounts(
                updateList(
                        gameServer.getVolumeMounts(),
                        dto.getVolumeMounts() != null
                                ? dto.getVolumeMounts().stream()
                                        .map(VolumeMountConfiguration::fromDto)
                                        .toList()
                                : null,
                        ArrayList::new));

        return gameServerRepository.save(gameServer);
    }

    @Async
    public void startServer(String gameServerUuid) {
        if (!startingServers.add(gameServerUuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server is already starting");
        }
        log.info("Starting server {}", gameServerUuid);
        try {
            GameServerEntity serverConfig =
                    transactionTemplate.execute(
                            status -> {
                                GameServerEntity entity = getGameServerById(gameServerUuid);

                                Hibernate.initialize(entity.getDockerExecutionCommand());
                                Hibernate.initialize(entity.getPortMappings());
                                Hibernate.initialize(entity.getEnvironmentVariables());
                                Hibernate.initialize(entity.getVolumeMounts());
                                return entity;
                            });

            enrichAndPublishLogMessage(
                    serverConfig,
                    GameServerLogMessageEntity.of(
                            serverConfig.getUuid(),
                            "Starting Game Server",
                            GameServerLogMessageEntity.LogLevel.COSY_DEBUG));

            updateStatus(serverConfig, GameServerDto.GameServerStatus.AWAITING_UPDATE);

            try {
                engineManager.startAndAttachLogListener(
                        serverConfig,
                        (logMessage) -> {
                            enrichAndPublishLogMessage(serverConfig, logMessage);
                        },
                        (startEvent) -> {
                            if (startEvent instanceof StartEventDto.PullProgress pullProgress) {
                                dockerProgressPublisher.publishDockerProgress(
                                        serverConfig.getUuid(), pullProgress.getProgress());
                            }
                        },
                        (status) -> updateStatus(serverConfig, status),
                        (a) ->
                                enrichAndPublishLogMessage(
                                        serverConfig,
                                        GameServerLogMessageEntity.of(
                                                serverConfig.getUuid(),
                                                "Starting to pull Docker Image",
                                                GameServerLogMessageEntity.LogLevel.COSY_DEBUG)),
                        (a) ->
                                enrichAndPublishLogMessage(
                                        serverConfig,
                                        GameServerLogMessageEntity.of(
                                                serverConfig.getUuid(),
                                                "Docker Image pulled successfully",
                                                GameServerLogMessageEntity.LogLevel.COSY_DEBUG)));
            } catch (InternalServiceStartException e) {
                log.error("Docker error while starting server '{}'", gameServerUuid, e);
                enrichAndPublishLogMessage(
                        serverConfig,
                        GameServerLogMessageEntity.of(
                                serverConfig.getUuid(),
                                e.getOriginalException().toString(),
                                GameServerLogMessageEntity.LogLevel.COSY_DEBUG));
                updateStatus(serverConfig, GameServerDto.GameServerStatus.FAILED);
            } catch (Exception e) {
                updateStatus(serverConfig, GameServerDto.GameServerStatus.FAILED);
                log.error("Error starting server '{}'", gameServerUuid, e);
                throw new RuntimeException("Error while starting docker container");
            }
        } catch (Exception e) {
            log.error("Error starting server '{}'", gameServerUuid, e);
            throw new RuntimeException("Error while starting docker container");
        } finally {
            startingServers.remove(gameServerUuid);
        }
    }

    public GameServerLogMessageEntity enrichAndPublishLogMessage(
            GameServerEntity gameServer, GameServerLogMessageEntity logMessage) {
        logMessage.setGameServerUuid(gameServer.getUuid());
        gameServerLogService.saveGameServerLog(logMessage);
        return logMessage;
    }

    public void stopServer(String serviceName) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(serviceName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Server '" + serviceName + "' not found"));
        enrichAndPublishLogMessage(
                gameServer,
                GameServerLogMessageEntity.of(
                        gameServer.getUuid(),
                        "Stopping Game Server",
                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG));
        updateStatus(gameServer, GameServerDto.GameServerStatus.AWAITING_UPDATE);
        try {
            engineManager.stopAndRemove(gameServer);
        } catch (ServerAlreadyStoppedException e) {
            log.info("Server '{}' was already stopped", serviceName);
            gameServer.setStatus(GameServerDto.GameServerStatus.STOPPED);
            gameServerRepository.save(gameServer);
            statusPublisher.publishStatus(
                    gameServer.getUuid(), GameServerDto.GameServerStatus.STOPPED);
        } catch (Exception e) {
            log.error("Error stopping server '{}'", serviceName, e);
            throw e;
        }
    }

    public void updateStatus(GameServerEntity serverConfig, GameServerDto.GameServerStatus status) {
        serverConfig.setStatus(status);
        gameServerRepository.save(serverConfig);
        statusPublisher.publishStatus(serverConfig.getUuid(), status);
    }

    public GameServerDto.GameServerStatus getStatus(String serviceName) {
        GameServerEntity server =
                gameServerRepository
                        .findById(serviceName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Server '" + serviceName + "' not found"));

        return server.getStatus();
    }

    public GameServerEntity convertDtoToEntity(GameServerCreationDto dto) {
        Optional<GameEntity> game =
                dto.getGameUuid() != null
                        ? gameEntityService.getGameFromUuid(dto.getGameUuid())
                        : Optional.empty();

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
