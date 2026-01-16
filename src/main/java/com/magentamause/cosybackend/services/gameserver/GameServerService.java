package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
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
import lombok.RequiredArgsConstructor;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

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

    // @Transactional removed to allow immediate status updates (PULLING_IMAGE)
    public Flux<StartEventDto> startServer(String serviceName) {
        return Flux.create(
                sink -> {
                    if (!startingServers.add(serviceName)) {
                        sink.error(
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Server '" + serviceName + "' is already starting"));
                        return;
                    }

                    try {
                        GameServerEntity config =
                                transactionTemplate.execute(
                                        status -> {
                                            GameServerEntity entity =
                                                    gameServerRepository
                                                            .findById(serviceName)
                                                            .orElseThrow(
                                                                    () ->
                                                                            new ResponseStatusException(
                                                                                    HttpStatus
                                                                                            .NOT_FOUND,
                                                                                    "Server '"
                                                                                            + serviceName
                                                                                            + "' not found"));
                                            Hibernate.initialize(
                                                    entity.getDockerExecutionCommand());
                                            Hibernate.initialize(entity.getPortMappings());
                                            Hibernate.initialize(entity.getEnvironmentVariables());
                                            Hibernate.initialize(entity.getVolumeMounts());
                                            return entity;
                                        });

                        enrichAndPublishLogMessage(
                                config,
                                GameServerLogMessageEntity.of(
                                        config.getUuid(),
                                        "Starting Game Server",
                                        GameServerLogMessageEntity.LogLevel.DEBUG));

                        List<Integer> ports =
                                engineManager.startAndAttachLogListener(
                                        config,
                                        (logMessage) -> {
                                            enrichAndPublishLogMessage(config, logMessage);
                                        },
                                        (startEvent) -> {
                                            if (startEvent
                                                    instanceof
                                                    StartEventDto.PullProgress
                                                    pullProgress) {
                                                dockerProgressPublisher.publishDockerProgress(
                                                        config.getUuid(),
                                                        pullProgress.getProgress());
                                            }
                                            sink.next(startEvent);
                                        },
                                        (status) -> updateStatus(config, status));

                        sink.next(StartEventDto.Done.fromPorts(ports));
                        enrichAndPublishLogMessage(
                                config,
                                GameServerLogMessageEntity.of(
                                        config.getUuid(),
                                        "Started Game Server",
                                        GameServerLogMessageEntity.LogLevel.DEBUG));
                        sink.complete();

                    } catch (Exception e) {
                        log.error("Error starting server '{}'", serviceName, e);
                        sink.error(e);
                    } finally {
                        startingServers.remove(serviceName);
                    }
                });
    }

    public GameServerLogMessageEntity enrichAndPublishLogMessage(
            GameServerEntity gameServer, GameServerLogMessageEntity logMessage) {

        logMessage.setGameServerUuid(gameServer.getUuid());
        gameServerLogService.saveGameServerLog(logMessage);

        gameServerLogWebsocketPublisher.publishLog(gameServer.getUuid(), logMessage);
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
                        GameServerLogMessageEntity.LogLevel.DEBUG));
        try {
            engineManager.stop(gameServer);
            enrichAndPublishLogMessage(
                    gameServer,
                    GameServerLogMessageEntity.of(
                            gameServer.getUuid(),
                            "Stopped Game Server",
                            GameServerLogMessageEntity.LogLevel.DEBUG));
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
