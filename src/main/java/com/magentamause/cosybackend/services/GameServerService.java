package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.EngineType;
import com.magentamause.cosybackend.engine.config.EngineProperties;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GameServerService {

    private final GameServerRepository gameServerRepository;
    private final EngineManager engineManager;
    private final EngineType engineType;

    public GameServerService(
            EngineManager engineManager,
            EngineProperties engineProperties,
            GameServerRepository gameServerRepository) {

        this.engineManager = engineManager;
        this.engineType = engineProperties.selected();
        this.gameServerRepository = gameServerRepository;

        log.info("GameServerService initialized with engine '{}'", engineType);
    }

    @Transactional
    public List<Integer> startServer(String serviceName) {
        GameServerConfigurationEntity config =
                gameServerRepository.findById(serviceName).orElseThrow();

        return engineManager.start(config);
    }

    public void stopServer(String serviceName) {
        engineManager.stop(gameServerRepository.findById(serviceName).orElseThrow());
    }

    public String getStatus(String serviceName) {
        return engineManager.status(gameServerRepository.findById(serviceName).orElseThrow());
    }
}
