package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import java.util.List;
import java.util.function.Consumer;

public interface EngineManager {
    List<Integer> start(
            GameServerEntity serviceConfig,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater);

    void stop(GameServerEntity serviceConfig);

    void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener);

    default List<Integer> startAndAttachLogListener(
            GameServerEntity serviceConfig,
            Consumer<GameServerLogMessageEntity> logListener,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater) {
        List<Integer> exposedPorts = start(serviceConfig, progressListener, statusUpdater);
        attachLogListener(serviceConfig, logListener);
        return exposedPorts;
    }
}
