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
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import com.magentamause.cosybackend.exceptions.McRouterException;
import com.magentamause.cosybackend.exceptions.RconBadAuthorizationException;
import com.magentamause.cosybackend.exceptions.RconException;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.security.SecretGenerator;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.policies.GameServerPolicy;
import com.magentamause.cosybackend.services.McRouterDomainValidationService;
import com.magentamause.cosybackend.services.core.games.GamesService;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.docker.McRouterContainerService;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareLimitPresentValidator;
import com.magentamause.cosybackend.services.engine.docker.util.HardwareQuotaChecker;
import com.magentamause.cosybackend.services.engine.docker.util.VolumeDirectoryService;
import com.magentamause.cosybackend.services.technical.RCONService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import com.magentamause.cosybackend.websockets.GameServerDockerProgressPublisher;
import com.magentamause.cosybackend.websockets.GameServerUpdatePublisher;
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
    private final GameServerUpdatePublisher gameServerUpdatePublisher;
    private final GameServerDockerProgressPublisher dockerProgressPublisher;
    private final GameServerLogService gameServerLogService;
    private final GamesService gamesService;
    private final HardwareLimitPresentValidator hardwareLimitValidator;
    private final HardwareQuotaChecker hardwareQuotaChecker;
    private final VolumeDirectoryService entry;
    private final RCONService rCONService;
    private final DefaultSettingsMapper defaultSettingsMapper;
    private final GameServerWebhookService webhookService;
    private final McRouterDomainValidationService mcRouterDomainValidationService;
    private final McRouterContainerService mcRouterContainerService;

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

        // Auto-stop mc-router if this was a Minecraft server with domains
        if (mcRouterContainerService.isMinecraftServer(gameServerEntity)
                && gameServerEntity.getMcRouterDomains() != null
                && !gameServerEntity.getMcRouterDomains().isEmpty()) {
            mcRouterContainerService.stopMcRouterIfNoServersNeedIt();
        }
    }

    private void handleGameServerEngineFailEvent(GameServerEntity gameServerEntity) {
        updateStatus(gameServerEntity, GameServerDto.GameServerStatus.FAILED);
        gameServerLogService.publishAndSaveLog(
                gameServerEntity,
                GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                "Docker game server failure event received",
                false);

        // Auto-stop mc-router if this was a Minecraft server with domains
        if (mcRouterContainerService.isMinecraftServer(gameServerEntity)
                && gameServerEntity.getMcRouterDomains() != null
                && !gameServerEntity.getMcRouterDomains().isEmpty()) {
            mcRouterContainerService.stopMcRouterIfNoServersNeedIt();
        }
    }

    public List<GameServerEntity> getAllGameServers() {
        return gameServerRepository.findAll();
    }

    public GameServerEntity getOrThrow(String uuid) {
        return getOptionalGameServerById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
    }

    public Optional<GameServerEntity> getOptionalGameServerById(String uuid) {
        return gameServerRepository.findById(uuid);
    }

    public GameServerEntity createGameServer(UserEntity user, GameServerCreationDto gameServerDto) {
        Function<Integer, GameEntity> gameResolver =
                (externalGameId) -> gamesService.getGameEntityByExternalId(externalGameId, true);

        GameServerEntity created = gameServerDto.toEntity(user, gameResolver);

        defaultSettingsMapper.createDefaultLayouts(created);

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
        if (!gameServer.getStatus().isStopped()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot update server while it is running");
        }

        Set<String> oldVolumeUuids =
                gameServer.getVolumeMounts() != null
                        ? gameServer.getVolumeMounts().stream()
                                .map(VolumeMountConfiguration::getUuid)
                                .filter(id -> id != null)
                                .collect(Collectors.toSet())
                        : Set.of();

        Function<Integer, GameEntity> gameResolver =
                (externalGameId) -> gamesService.getGameEntityByExternalId(externalGameId, true);

        updateDto.applyToEntity(gameServer, gameResolver);

        GameServerEntity saved = saveGameServerConfiguration(gameServer, false);

        Set<String> newVolumeUuids =
                saved.getVolumeMounts() != null
                        ? saved.getVolumeMounts().stream()
                                .map(VolumeMountConfiguration::getUuid)
                                .filter(id -> id != null)
                                .collect(Collectors.toSet())
                        : Set.of();

        List<String> removedUuids =
                oldVolumeUuids.stream().filter(id -> !newVolumeUuids.contains(id)).toList();

        if (!removedUuids.isEmpty()) {
            entry.deleteVolumeDirectoriesByUuids(removedUuids);
        }

        return saved;
    }

    public void startServer(String gameServerUuid) {
        if (!startingServers.add(gameServerUuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server is already starting");
        }
        GameServerEntity serverConfig = getOrThrow(gameServerUuid);
        if (!serverConfig.getStatus().isStopped()) {
            startingServers.remove(gameServerUuid);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Server is not in a stopped state");
        }
        UserEntity gameServerOwner = serverConfig.getOwner();
        try {
            synchronized (gameServerOwner.getUuid().intern()) {
                List<GameServerEntity> gameServersByOwner =
                        getGameServersByOwner(gameServerOwner.getUuid());
                hardwareQuotaChecker.assertSufficientQuota(
                        gameServerOwner, serverConfig, gameServersByOwner);

                // Validate mc-router domains if configured
                mcRouterDomainValidationService.validateDomainsForStart(
                        serverConfig, gameServerOwner);

                // Auto-start mc-router if this server has domains configured
                if (mcRouterContainerService.isMinecraftServer(serverConfig)
                        && serverConfig.getMcRouterDomains() != null
                        && !serverConfig.getMcRouterDomains().isEmpty()) {
                    mcRouterContainerService.ensureMcRouterRunningIfNeeded(serverConfig);
                }

                startServerAsync(gameServerUuid, serverConfig);
            }
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
        } catch (McRouterException e) {
            startingServers.remove(gameServerUuid);
            log.warn(
                    "Could not start Server '{}' - MC-Router domain validation failed: {}",
                    gameServerUuid,
                    e.getMessage());
            gameServerLogService.publishAndSaveLog(
                    serverConfig,
                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                    "MC-Router domain validation failed: " + e.getMessage(),
                    false);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "MC-Router domain validation failed: " + e.getMessage());
        } catch (Exception e) {
            startingServers.remove(gameServerUuid);
            throw e;
        }
    }

    @Async
    protected void startServerAsync(String gameServerUuid, GameServerEntity serverConfig) {
        log.info("Starting server {}", gameServerUuid);
        try {
            String gameServerContainerSecret = SecretGenerator.generateSecret();
            gameServerLogService.publishAndSaveLog(
                    serverConfig,
                    GameServerLogMessageEntity.LogLevel.COSY_DEBUG,
                    "Starting Game Server",
                    false);
            serverConfig.setContainerSecret(gameServerContainerSecret);
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
        GameServerDto.GameServerStatus previousStatus = serverConfig.getStatus();
        serverConfig.setStatus(status);
        GameServerEntity savedServer = gameServerRepository.save(serverConfig);
        gameServerUpdatePublisher.publishGameServerUpdate(savedServer);
        webhookService.handleStatusTransition(
                serverConfig.getUuid(), serverConfig.getServerName(), previousStatus, status);
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

    private List<GameServerEntity> getGameServersByOwner(String ownerUuid) {
        return gameServerRepository.findByOwner_Uuid(ownerUuid);
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
                                gameServer.getPublicDashboard().isEnabled()
                                        || GameServerPolicy.canGetGameServer(
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
        Optional<GameServerEntity> gameServer = getOptionalGameServerById(uuid);
        if (gameServer.isEmpty()) {
            return false;
        }
        return gameServer.get().getContainerSecret().equals(secret);
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
        return entry instanceof Integer
                || entry instanceof Long
                || entry instanceof Float
                || entry instanceof Double
                || entry instanceof String
                || entry instanceof Boolean;
    }

    public List<GameServerEntity> getPubliclyEvaluableGameServer() {
        List<GameServerEntity> allGameServers = getAllGameServers();

        return allGameServers.stream()
                .filter(gameServer -> gameServer.getPublicDashboard().isEnabled())
                .toList();
    }

    public boolean isGameServerPubliclyEvaluable(String gameServerUuid) {
        return getOptionalGameServerById(gameServerUuid)
                .map(
                        gameServer ->
                                gameServer.getPublicDashboard() != null
                                        && gameServer.getPublicDashboard().isEnabled())
                .orElse(false);
    }
}
