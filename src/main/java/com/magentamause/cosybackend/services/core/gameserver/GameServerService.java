package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.TransferOwnershipDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import com.magentamause.cosybackend.exceptions.RconBadAuthorizationException;
import com.magentamause.cosybackend.exceptions.RconException;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.security.SecretGenerator;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.policies.GameServerPolicy;
import com.magentamause.cosybackend.services.core.games.GamesService;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareLimitPresentValidator;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareQuotaChecker;
import com.magentamause.cosybackend.services.engine.docker.util.VolumeDirectoryService;
import com.magentamause.cosybackend.services.technical.RCONService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import com.magentamause.cosybackend.websockets.GameServerDockerProgressPublisher;
import com.magentamause.cosybackend.websockets.GameServerStatusPublisher;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
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
    private final UserEntityService userEntityService;
    private final EngineManager engineManager;
    private final Set<String> startingServers = ConcurrentHashMap.newKeySet();
    private final GameServerStatusPublisher statusPublisher;
    private final GameServerDockerProgressPublisher dockerProgressPublisher;
    private final GameServerLogService gameServerLogService;
    private final GamesService gamesService;
    private final HardwareLimitPresentValidator hardwareLimitValidator;
    private final HardwareQuotaChecker hardwareQuotaChecker;
    private final VolumeDirectoryService entry;
    private final RCONService rCONService;

    @PostConstruct
    public void init() {
        engineManager.attachStatusListener(this::handleGameServerEngineEvent);
        for (GameServerEntity server : gameServerRepository.findAll()) {
            GameServerDto.GameServerStatus status = engineManager.getStatus(server);
            updateStatus(server, status);
            engineManager.attachStatusSupplier(
                    server.getUuid(), () -> getStatusFromEntity(server.getUuid()));
            log.info("Setting status of server {} to {} ", server.getUuid(), status);

            if (status == GameServerDto.GameServerStatus.RUNNING) {
                try {
                    log.info("Re-attaching log listener for running server {}", server.getUuid());
                    engineManager.attachLogListener(
                            server,
                            (logMessage) ->
                                    gameServerLogService.publishAndSaveLog(logMessage, true));
                } catch (Exception e) {
                    log.warn("Failed to re-attach log listener for server {}", server.getUuid(), e);
                }
            }
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

    public GameServerEntity getOrThrow(String uuid) {
        return getOptionalGameServerOptionalById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
    }

    public Optional<GameServerEntity> getOptionalGameServerOptionalById(String uuid) {
        return gameServerRepository.findById(uuid);
    }

    public GameServerEntity createGameServer(UserEntity user, GameServerCreationDto gameServerDto) {
        Function<Integer, GameEntity> gameResolver =
                (externalGameId) -> gamesService.getGameEntityByExternalId(externalGameId, true);

        GameServerEntity created = gameServerDto.toEntity(user, gameResolver);
        return saveGameServerConfiguration(created, true);
    }

    GameServerEntity saveGameServerConfiguration(GameServerEntity entity, boolean isNew) {
        hardwareLimitValidator.validateHardwareLimitsPresent(
                entity.getOwner().getDockerHardwareLimits(), entity.getDockerHardwareLimits());
        if (isNew) {
            entity.setUuid(null);
            entity.setStatus(GameServerDto.GameServerStatus.STOPPED);
        }
        log.info("Saving game server {}", entity);

        GameServerEntity saved = gameServerRepository.save(entity);
        entry.assertVolumeDirectoriesExist(saved);
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
        entry.deleteVolumeDirectories(gameServer);
    }

    public GameServerEntity updateGameServerConfiguration(
            String uuid, GameServerUpdateDto updateDto) {
        GameServerEntity gameServer = getOrThrow(uuid);

        Function<Integer, GameEntity> gameResolver =
                (externalGameId) -> gamesService.getGameEntityByExternalId(externalGameId, true);

        updateDto.applyToEntity(gameServer, gameResolver);

        return saveGameServerConfiguration(gameServer, false);
    }

    public void startServer(String gameServerUuid, UserEntity user) {
        if (!startingServers.add(gameServerUuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server is already starting");
        }
        GameServerEntity serverConfig = getOrThrow(gameServerUuid);
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
            String gameServerContainerSecret = SecretGenerator.generateSecret();
            gameServerLogService.publishAndSaveLog(
                    serverConfig,
                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                    "Starting Game Server",
                    false);
            serverConfig.setContainerSecret(gameServerContainerSecret);
            serverConfig.setLastStartedBy(serverConfig.getOwner());
            serverConfig.setTimestampLastStarted(LocalDateTime.now());
            gameServerRepository.save(serverConfig);

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
                gameServerLogService.publishAndSaveLog(
                        serverConfig,
                        GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                        "Failed to pull Docker Image: " + e.getMessage(),
                        false);
            }
        } finally {
            startingServers.remove(gameServerUuid);
        }
    }

    private GameServerDto.GameServerStatus getStatusFromEntity(String uuid) {
        return getOrThrow(uuid).getStatus();
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

    public void sendCommand(String uuid, String command) {
        GameServerEntity gameServer = getOrThrow(uuid);
        gameServerLogService.publishAndSaveLog(
                gameServer, GameServerLogMessageEntity.LogLevel.INPUT, command, false);
        try {
            log.info("Sending command '{}' to server {}", command, uuid);
            if (gameServer.getRconConfiguration() != null
                    && gameServer.getRconConfiguration().isEnabled()) {
                Consumer<String> logCallback =
                        (log) ->
                                gameServerLogService.publishAndSaveLog(
                                        gameServer,
                                        GameServerLogMessageEntity.LogLevel.INFO,
                                        log,
                                        true);
                rCONService.sendCommand(
                        gameServer.getRconConfiguration().getPort(),
                        gameServer.getRconConfiguration().getPassword(),
                        command,
                        logCallback);
            } else {
                engineManager.sendCommand(gameServer, command);
            }
        } catch (IOException e) {
            gameServerLogService.publishAndSaveLog(
                    gameServer,
                    GameServerLogMessageEntity.LogLevel.COSY_ERROR,
                    "Docker attach exception: " + e.getMessage(),
                    false);
        } catch (RconBadAuthorizationException e) {
            gameServerLogService.publishAndSaveLog(
                    gameServer,
                    GameServerLogMessageEntity.LogLevel.COSY_ERROR,
                    "RCON authorization exception: " + e.getMessage(),
                    false);
        } catch (RconException e) {
            gameServerLogService.publishAndSaveLog(
                    gameServer,
                    GameServerLogMessageEntity.LogLevel.COSY_ERROR,
                    "RCON IO exception: " + e.getMessage(),
                    false);
        }
    }

    public List<GameServerEntity> getGameServersVisibleToUser(UserEntity user) {
        List<GameServerEntity> allGameServers = getAllGameServers();
        if (user.getRole().isAdmin()) {
            return allGameServers;
        }
        return allGameServers.stream()
                .filter(
                        gameServer ->
                                GameServerPolicy.canGetGameServer(
                                        ResourceResolver.of(gameServer),
                                        gameServer.getUuid(),
                                        user))
                .toList();
    }

    public Map<String, Object> updateCustomMetric(
            String uuid, String secret, Map<String, Object> value) {
        if (!value.values().stream().allMatch(this::isCustomMetricEntryValid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid custom metric");
        }

        GameServerEntity gameServer = getOrThrow(uuid);
        if (!gameServer.getContainerSecret().equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid secret");
        }
        gameServer.setCustomMetricHolder(value);
        return gameServerRepository.save(gameServer).getCustomMetricHolder();
    }

    public Boolean checkGameServerConnection(String uuid, String secret) {
        GameServerEntity gameServer = getOrThrow(uuid);
        return gameServer.getContainerSecret().equals(secret);
    }

    public GameServerEntity transferGameServerOwnership(
            String gameServerUuid, TransferOwnershipDto transferOwnershipDto) {
        GameServerEntity gameServer = getOrThrow(gameServerUuid);
        UserEntity oldOwner = gameServer.getOwner();

        if (gameServer.getStatus() != GameServerDto.GameServerStatus.STOPPED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Can't change the owner while the server is running");
        }
        UserEntity newOwner =
                userEntityService.getUserByUsername(transferOwnershipDto.getNewOwnerName());
        gameServer.setOwner(newOwner);
        log.info(
                "Changing owner of server {} from {} to {}",
                gameServerUuid,
                oldOwner.getUsername(),
                gameServer.getOwner().getUsername());
        return saveGameServerConfiguration(gameServer, false);
    }

    private boolean isCustomMetricEntryValid(Object entry) {
        return entry instanceof Integer ||
                entry instanceof Long ||
                entry instanceof Float ||
                entry instanceof Double ||
                entry instanceof String ||
                entry instanceof Boolean;
    }
}
