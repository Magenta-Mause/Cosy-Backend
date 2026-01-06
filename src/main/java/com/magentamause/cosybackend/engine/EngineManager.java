package com.magentamause.cosybackend.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import java.util.List;

public interface EngineManager {
    List<Integer> start(GameServerConfigurationEntity serviceConfig);

    void stop(GameServerConfigurationEntity serviceConfig);

    GameServerStatusDto status(GameServerConfigurationEntity serviceConfig);
}
