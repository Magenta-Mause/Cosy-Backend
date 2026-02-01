package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.core.games.GamesService;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareLimitPresentValidator;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareQuotaChecker;
import com.magentamause.cosybackend.services.engine.docker.util.VolumeDirectoryService;
import com.magentamause.cosybackend.websockets.GameServerDockerProgressPublisher;
import com.magentamause.cosybackend.websockets.GameServerStatusPublisher;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerService {

    private final GameServerRepository gameServerRepository;
    private final EngineManager engineManager;
    private final Set<String> startingServers = ConcurrentHashMap.newKeySet();
    private final GameServerStatusPublisher statusPublisher;
    private final GameServerDockerProgressPublisher dockerProgressPublisher;
    private final GameServerLogService gameServerLogService;
    private final GamesService gamesService;
    private final HardwareLimitPresentValidator hardwareLimitValidator;
    private final HardwareQuotaChecker hardwareQuotaChecker;
    private final VolumeDirectoryService volumeDirectoryService;

    @PostConstruct
    public void init() {
        engineManager.attachStatusListener(this::handleGameServerEngineEvent);
        for (GameServerEntity server : gameServerRepository.findAll()) {
            GameServerDto.GameServerStatus status = engineManager.getStatus(server);
            updateStatus(server, status);
            engineManager.attachStatusSupplier(
                    server.getUuid(), () -> getStatusFromEntity(server.getUuid()));
            log.info("Setting status of server {} to {} ", server.getUuid(), status);
        }
    }

    private void handleGameServerEngineEvent(
            GameServerStatusUpdateEventType type, String gameServerUuid) {
        Optional<GameServerEntity> server = gameServerRepository.findById(gameServerUuid);
        server.ifPresent(
                gameServerEntity -> {
                    switch (type) {
                        case STARTED -> handleGameServerEngineStartEvent(gameServerEntity);
                        case STOPPED -> handleGameServerEngineStopEvent(gameServerEntity);
                        case FAILED -> handleGameServerEngineFailEvent(gameServerEntity);
                    }
                });
    }

    private void handleGameServerEngineStartEvent(GameServerEntity gameServerEntity) {
        updateStatus(gameServerEntity, GameServerDto.GameServerStatus.RUNNING);
        gameServerLogService.publishAndSaveLog(
                gameServerEntity,
                GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                "Docker game server start event received",
                false);
        String exposedPorts =
                gameServerEntity.getPortMappings().stream()
                        .map(PortMapping::getInstancePort)
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
        gameServerLogService.publishAndSaveLog(
                gameServerEntity,
                GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                "Exposed ports: " + exposedPorts,
                false);
    }

    private void handleGameServerEngineStopEvent(GameServerEntity gameServerEntity) {
        updateStatus(gameServerEntity, GameServerDto.GameServerStatus.STOPPED);
        gameServerLogService.publishAndSaveLog(
                gameServerEntity,
                GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                "Docker game server stop event received",
                false);
    }

    private void handleGameServerEngineFailEvent(GameServerEntity gameServerEntity) {
        updateStatus(gameServerEntity, GameServerDto.GameServerStatus.FAILED);
        gameServerLogService.publishAndSaveLog(
                gameServerEntity,
                GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                "Docker game server failure event received",
                false);
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

    public GameServerEntity createGameServer(UserEntity user, GameServerCreationDto gameServerDto) {
        GameEntity game =
                gamesService
                        .getOptionalGameByExternalId(gameServerDto.getExternalGameId(), true)
                        .orElse(null);
        GameServerEntity created = gameServerDto.toEntity(user, game);
        return saveGameServerConfiguration(created, true);
    }

    private GameServerEntity saveGameServerConfiguration(GameServerEntity entity, boolean isNew) {
        hardwareLimitValidator.validateHardwareLimitsPresent(
                entity.getOwner().getDockerHardwareLimits(), entity.getDockerHardwareLimits());
        if (isNew) {
            entity.setUuid(null);
            entity.setStatus(GameServerDto.GameServerStatus.STOPPED);
        }
        log.info("Saving game server {}", entity);

        GameServerEntity saved = gameServerRepository.save(entity);
        volumeDirectoryService.assertVolumeDirectoriesExist(saved);
        return saved;
    }

    public void deleteGameServerById(String uuid) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(uuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid " + uuid + " not found"));
        try {
            engineManager.stopAndRemove(gameServer);
        } catch (ServerAlreadyStoppedException e) {
            log.debug("Server '{}' was already stopped when attempting to delete", uuid, e);
        }
        gameServerRepository.deleteById(uuid);
    }

    public GameServerEntity updateGameServerConfiguration(
            String uuid, GameServerUpdateDto updateDto) {
        GameServerEntity gameServer = getGameServerById(uuid);

        GameEntity game =
                gamesService.getGameEntityByExternalId(updateDto.getExternalGameId(), true);

        updateDto.applyToEntity(gameServer, game);

        return saveGameServerConfiguration(gameServer, false);
    }

    public void startServer(String gameServerUuid, UserEntity user) {
        if (!startingServers.add(gameServerUuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server is already starting");
        }
        GameServerEntity serverConfig = getGameServerById(gameServerUuid);
        try {
            List<GameServerEntity> gameServerStartedByUser =
                    getGameServersStartedByUser(user.getUuid());
            hardwareQuotaChecker.assertSufficientQuota(user, serverConfig, gameServerStartedByUser);

            startServerAsync(gameServerUuid, serverConfig);
        } catch (HardwareLimitException e) {
            startingServers.remove(gameServerUuid);
            log.warn("Could not start Server '{}' - Hardware quota limit reached.", gameServerUuid);
            gameServerLogService.publishAndSaveLog(
                    serverConfig,
                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                    "Hardware quota limit reached: " + e.getMessage(),
                    false);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Hardware quota limit reached: " + e.getMessage());
        } catch (Exception e) {
            startingServers.remove(gameServerUuid);
            throw e;
        }
    }

    @Async
    void startServerAsync(String gameServerUuid, GameServerEntity serverConfig) {
        log.info("Starting server {}", gameServerUuid);
        try {

            gameServerLogService.publishAndSaveLog(
                    serverConfig,
                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                    "Starting Game Server",
                    false);

            updateStatus(serverConfig, GameServerDto.GameServerStatus.AWAITING_UPDATE);

            try {
                engineManager.startAndAttachLogListener(
                        serverConfig,
                        (logMessage) -> {
                            gameServerLogService.publishAndSaveLog(logMessage, true);
                        },
                        (startEvent) -> {
                            if (startEvent instanceof StartEventDto.PullProgress pullProgress) {
                                dockerProgressPublisher.publishDockerProgress(
                                        serverConfig.getUuid(), pullProgress.getProgress());
                            }
                        },
                        (status) -> updateStatus(serverConfig, status),
                        (ignored) ->
                                gameServerLogService.publishAndSaveLog(
                                        serverConfig,
                                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                                        "Starting to pull Docker Image",
                                        false),
                        (ignored) ->
                                gameServerLogService.publishAndSaveLog(
                                        serverConfig,
                                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                                        "Docker Image pulled successfully",
                                        false),
                        () -> getStatusFromEntity(serverConfig.getUuid()));
            } catch (InternalServiceStartException e) {
                log.error("Docker error while starting server '{}'", gameServerUuid, e);
                gameServerLogService.publishAndSaveLog(
                        serverConfig,
                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                        e.getOriginalException().toString(),
                        false);
                updateStatus(serverConfig, GameServerDto.GameServerStatus.FAILED);
            } catch (DockerPullImageException e) {
                updateStatus(serverConfig, GameServerDto.GameServerStatus.FAILED);
                log.warn("Failed to pull docker image for server '{}'", gameServerUuid, e);
                gameServerLogService.publishAndSaveLog(
                        serverConfig,
                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                        "Failed to pull Docker Image: " + e.getImageName(),
                        false);
            } catch (Exception e) {
                updateStatus(serverConfig, GameServerDto.GameServerStatus.FAILED);
                log.error("Error starting server '{}'", gameServerUuid, e);
                throw new RuntimeException(
                        "Error while starting docker container: " + e.getMessage(), e);
            }
        } finally {
            startingServers.remove(gameServerUuid);
        }
    }

    private GameServerDto.GameServerStatus getStatusFromEntity(String uuid) {
        return getGameServerById(uuid).getStatus();
    }

    @Async
    public void stopServer(String serviceName) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(serviceName)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Server '" + serviceName + "' not found"));
        gameServerLogService.publishAndSaveLog(
                gameServer,
                GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                "Stopping Game Server",
                false);
        updateStatus(gameServer, GameServerDto.GameServerStatus.STOPPING);
        try {
            engineManager.stopAndRemove(gameServer);
        } catch (ServerAlreadyStoppedException e) {
            log.info("Server '{}' was already stopped", serviceName);
            updateStatus(gameServer, GameServerDto.GameServerStatus.STOPPED);
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

    private List<GameServerEntity> getGameServersStartedByUser(String userUuid) {
        return gameServerRepository.findByLastStartedBy_Uuid(userUuid);
    }
}
