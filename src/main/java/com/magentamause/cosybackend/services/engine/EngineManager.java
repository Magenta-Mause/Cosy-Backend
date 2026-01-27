package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
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

    void attachStatusListener(BiConsumer<GameServerStatusUpdateEventType, String> listener);

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

    Optional<Metric> collectMetric(GameServerEntity serviceConfig) throws InterruptedException;
}
