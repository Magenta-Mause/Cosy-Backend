package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import java.util.List;

public interface EngineManager {
    List<Integer> start(GameServerEntity serviceConfig);

    void stop(GameServerEntity serviceConfig);

    GameServerStatusDto status(GameServerEntity serviceConfig);
}
