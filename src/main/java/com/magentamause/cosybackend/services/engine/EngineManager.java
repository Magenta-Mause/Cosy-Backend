package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface EngineManager {
    void start(
            GameServerEntity serviceConfig,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback)
            throws InternalServiceStartException;

    void stop(GameServerEntity serviceConfig);

    void remove(GameServerEntity serviceConfig);

    void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener);

    void attachStatusListener(
            GameServerEntity serviceConfig,
            Supplier<GameServerDto.GameServerStatus> currentStatusSupplier,
            Consumer<GameServerDto.GameServerStatus> listener);

    void attachStartListener(Consumer<String> listener);

    void attachStopListener(Consumer<String> listener);

    void attachFailListener(Consumer<String> listener);

    default void startAndAttachLogListener(
            GameServerEntity serviceConfig,
            Consumer<GameServerLogMessageEntity> logListener,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback)
            throws InternalServiceStartException {
        start(
                serviceConfig,
                progressListener,
                statusUpdater,
                imagePullStartCallback,
                imagePullEndCallback);
        attachLogListener(serviceConfig, logListener);
    }
}
