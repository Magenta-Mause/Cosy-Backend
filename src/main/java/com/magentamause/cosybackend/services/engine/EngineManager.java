package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface EngineManager {
    void start(
            GameServerEntity serviceConfig,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback,
            Supplier<GameServerDto.GameServerStatus> gameServerStatusSupplier)
            throws InternalServiceStartException, DockerPullImageException;

    void stopAndRemove(GameServerEntity serviceConfig);

    GameServerDto.GameServerStatus getStatus(GameServerEntity serverConfig);

    void attachStatusSupplier(
            String gameServerUuid, Supplier<GameServerDto.GameServerStatus> statusSupplier);

    void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener);

    /** Whether logs of the given server are currently being streamed. */
    boolean isLogListenerAttached(String gameServerUuid);

    void attachStatusListener(BiConsumer<GameServerStatusUpdateEventType, String> listener);

    /**
     * Registers a callback invoked whenever the engine's event subscription has been
     * (re)established.
     *
     * <p>Status events emitted while the subscription was down are lost, so listeners must use this
     * to re-reconcile their view of the world against the engine.
     */
    void attachConnectionListener(Runnable listener);

    default void startAndAttachLogListener(
            GameServerEntity serviceConfig,
            Consumer<GameServerLogMessageEntity> logListener,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback,
            Supplier<GameServerDto.GameServerStatus> gameServerStatusSupplier)
            throws InternalServiceStartException, DockerPullImageException {
        start(
                serviceConfig,
                progressListener,
                statusUpdater,
                imagePullStartCallback,
                imagePullEndCallback,
                gameServerStatusSupplier);
        attachLogListener(serviceConfig, logListener);
    }

    /**
     * The host ports currently bound by the engine, across all of its containers — including
     * containers this Cosy instance does not manage.
     *
     * <p>Best effort: implementations return an empty list instead of failing when the engine
     * cannot be queried, and the result is stale the moment it is returned. The engine stays the
     * final arbiter of whether a binding succeeds.
     */
    List<PublishedPort> getPublishedHostPorts();

    Optional<Metric> collectMetric(GameServerEntity serviceConfig) throws InterruptedException;

    void sendCommand(GameServerEntity serverConfig, String command) throws IOException;
}
