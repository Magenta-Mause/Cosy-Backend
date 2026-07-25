package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.dtos.actiondtos.TransferOwnershipDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.HostVolumeMountConfigurationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.HostVolumeMountConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
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
import com.magentamause.cosybackend.websockets.GameServerUpdatePublisher;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerService {

    /**
     * Placeholder for the periodic reconciliation interval. Kept as a constant because
     * {@code @Scheduled} only accepts compile-time constant strings.
     */
    private static final String RECONCILIATION_INTERVAL_PROPERTY =
            "${cosy.engine.reconciliation.interval-ms:"
                    + EngineProperties.Reconciliation.DEFAULT_INTERVAL_MS
                    + "}";

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
    private final EngineProperties engineProperties;

    /**
     * When each server's status last changed, used to leave in-flight start/stop operations alone
     * during reconciliation. Kept in memory on purpose: it is a guard for the current process, not
     * persisted state.
     */
    private final Map<String, Instant> lastStatusChange = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        engineManager.attachStatusListener(this::handleGameServerEngineEvent);
        // Events emitted while the engine's event subscription was down are unrecoverable, so every
        // (re)connect has to re-derive the persisted status from the real container state.
        engineManager.attachConnectionListener(this::reconcileAllStatuses);
        reconcileAllStatuses();
    }

    /**
     * Re-derives the persisted status of every game server from the real container state and makes
     * sure running servers have their log stream attached.
     *
     * <p>This runs at startup, after every (re)connect of the engine's event subscription and
     * periodically, so a status update that was never delivered — because the subscription was down
     * — cannot leave a server stuck in a transitional state forever.
     */
    @Scheduled(
            initialDelayString = RECONCILIATION_INTERVAL_PROPERTY,
            fixedDelayString = RECONCILIATION_INTERVAL_PROPERTY)
    public void reconcileAllStatuses() {
        for (GameServerEntity server : gameServerRepository.findAll()) {
            try {
                reconcileStatus(server);
            } catch (Exception e) {
                log.warn("Failed to reconcile status of server {}", server.getUuid(), e);
            }
        }
    }

    private void reconcileStatus(GameServerEntity server) {
        String uuid = server.getUuid();
        GameServerDto.GameServerStatus engineStatus = engineManager.getStatus(server);
        GameServerDto.GameServerStatus persistedStatus = server.getStatus();

        engineManager.attachStatusSupplier(uuid, () -> getStatusFromEntity(uuid));

        if (matchesEngineStatus(persistedStatus, engineStatus)) {
            attachLogListenerIfMissing(server, engineStatus);
            return;
        }

        if (isTransitional(persistedStatus) && changedRecently(uuid)) {
            // A start or stop is most likely still in flight; the operation itself will publish the
            // final status.
            log.debug(
                    "Skipping reconciliation of server {} in transitional status {}",
                    uuid,
                    persistedStatus);
            return;
        }

        log.info(
                "Reconciling status of server {}: persisted {}, engine reports {}",
                uuid,
                persistedStatus,
                engineStatus);
        updateStatus(server, engineStatus);
        attachLogListenerIfMissing(server, engineStatus);
    }

    private void attachLogListenerIfMissing(
            GameServerEntity server, GameServerDto.GameServerStatus engineStatus) {
        if (engineStatus != GameServerDto.GameServerStatus.RUNNING
                || engineManager.isLogListenerAttached(server.getUuid())) {
            return;
        }
        try {
            log.info("Attaching log listener for running server {}", server.getUuid());
            engineManager.attachLogListener(
                    server,
                    (logMessage) -> gameServerLogService.publishAndSaveLog(logMessage, true));
        } catch (Exception e) {
            log.warn("Failed to attach log listener for server {}", server.getUuid(), e);
        }
    }

    /**
     * The engine only distinguishes running from not running. {@code FAILED} is a stopped state
     * that carries extra information for the user, so it is not overwritten with a plain {@code
     * STOPPED}.
     */
    private boolean matchesEngineStatus(
            GameServerDto.GameServerStatus persisted, GameServerDto.GameServerStatus engineStatus) {
        if (engineStatus == GameServerDto.GameServerStatus.RUNNING) {
            return persisted == GameServerDto.GameServerStatus.RUNNING;
        }
        return persisted.isStopped();
    }

    private boolean isTransitional(GameServerDto.GameServerStatus status) {
        return status == GameServerDto.GameServerStatus.AWAITING_UPDATE
                || status == GameServerDto.GameServerStatus.PULLING_IMAGE
                || status == GameServerDto.GameServerStatus.STOPPING;
    }

    private boolean changedRecently(String uuid) {
        Instant lastChange = lastStatusChange.get(uuid);
        return lastChange != null
                && Duration.between(lastChange, Instant.now())
                                .compareTo(
                                        Duration.ofMillis(
                                                engineProperties.reconciliation().gracePeriodMs()))
                        < 0;
    }

    private void handleGameServerEngineEvent(
            GameServerStatusUpdateEventType type, String gameServerUuid) {
        Optional<GameServerEntity> server = gameServerRepository.findById(gameServerUuid);
        if (server.isEmpty()) {
            // Expected: a deleted server's container still emits a die event afterwards.
            log.debug(
                    "Ignoring {} event for game server {}, which no longer exists",
                    type,
                    gameServerUuid);
            lastStatusChange.remove(gameServerUuid);
            return;
        }
        GameServerEntity gameServerEntity = server.get();
        switch (type) {
            case STARTED -> handleGameServerEngineStartEvent(gameServerEntity);
            case STOPPED -> handleGameServerEngineStopEvent(gameServerEntity);
            case FAILED -> handleGameServerEngineFailEvent(gameServerEntity);
        }
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
        if (gameServerDto.getHostVolumeMounts() != null
                && !gameServerDto.getHostVolumeMounts().isEmpty()
                && !user.getRole().isAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only administrators may configure host volume mounts");
        }

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
            UserEntity user, String uuid, GameServerUpdateDto updateDto) {
        GameServerEntity gameServer = getOrThrow(uuid);
        if (!gameServer.getStatus().isStopped()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot update server while it is running");
        }

        boolean isAdmin = user != null && user.getRole().isAdmin();
        if (!isAdmin && hostMountsChanged(gameServer, updateDto)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only administrators may configure host volume mounts");
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

        // Admins/owners fully CRUD host mounts; for non-admins the existing host mounts are
        // preserved untouched (applyHostMounts = false).
        updateDto.applyToEntity(gameServer, gameResolver, isAdmin);

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
        String uuid = serverConfig.getUuid();
        GameServerDto.GameServerStatus previousStatus = serverConfig.getStatus();
        lastStatusChange.put(uuid, Instant.now());
        serverConfig.setStatus(status);

        GameServerEntity savedServer;
        try {
            savedServer = gameServerRepository.save(serverConfig);
        } catch (ObjectOptimisticLockingFailureException | EmptyResultDataAccessException e) {
            // Writing the status of a server that was deleted in the meantime updates zero rows.
            // That is a benign race — the container of a deleted server still emits its die event —
            // but the resulting exception used to propagate all the way out of the Docker event
            // callback and kill the event subscription for the rest of the process lifetime.
            if (!vanished(uuid)) {
                // The row is still there, so this is a genuine concurrent modification.
                throw e;
            }
            log.info(
                    "Dropping status update to {} for game server {}, which no longer exists",
                    status,
                    uuid);
            lastStatusChange.remove(uuid);
            return;
        }

        gameServerUpdatePublisher.publishGameServerUpdate(savedServer);
        webhookService.handleStatusTransition(
                uuid, serverConfig.getServerName(), previousStatus, status);
    }

    private boolean vanished(String uuid) {
        return uuid == null || !gameServerRepository.existsById(uuid);
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

    /**
     * Determines whether a non-admin update would alter the server's host volume mounts. A {@code
     * null} {@code hostVolumeMounts} field means "leave unchanged" and is never a change. Otherwise
     * the requested set is compared (order-insensitive) against the persisted mounts.
     */
    private boolean hostMountsChanged(GameServerEntity gameServer, GameServerUpdateDto updateDto) {
        List<HostVolumeMountConfigurationDto> requested = updateDto.getHostVolumeMounts();
        if (requested == null) {
            return false;
        }
        List<HostVolumeMountConfiguration> existing =
                gameServer.getHostVolumeMounts() != null
                        ? gameServer.getHostVolumeMounts()
                        : List.of();

        Set<String> existingKeys =
                existing.stream().map(this::hostMountKey).collect(Collectors.toSet());
        Set<String> requestedKeys =
                requested.stream().map(this::hostMountKey).collect(Collectors.toSet());
        return !existingKeys.equals(requestedKeys);
    }

    private String hostMountKey(HostVolumeMountConfiguration mount) {
        return mount.getHostPath() + "|" + mount.getContainerPath() + "|" + mount.isReadOnly();
    }

    private String hostMountKey(HostVolumeMountConfigurationDto mount) {
        boolean readOnly = mount.getReadOnly() == null || mount.getReadOnly();
        return mount.getHostPath() + "|" + mount.getContainerPath() + "|" + readOnly;
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
