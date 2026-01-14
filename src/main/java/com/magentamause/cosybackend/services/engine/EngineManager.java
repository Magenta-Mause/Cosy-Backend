package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.GameServerLogMessageEntity;
import java.util.List;
import java.util.function.Consumer;

public interface EngineManager {
    List<Integer> start(GameServerEntity serviceConfig);

    void stop(GameServerEntity serviceConfig);

    void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener);

    GameServerStatusDto status(GameServerEntity serviceConfig);

    default List<Integer> startAndAttachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener) {
        List<Integer> exposedPorts = start(serviceConfig);
        attachLogListener(serviceConfig, listener);
        return exposedPorts;
    }

    List<String> getActiveContainerUuids();
}
