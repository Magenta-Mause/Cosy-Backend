package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.EngineType;
import com.magentamause.cosybackend.engine.config.EngineProperties;
import com.magentamause.cosybackend.engine.docker.DockerEngineManager;
import com.magentamause.cosybackend.engine.kubernetes.KubernetesEngineManager;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GameServerService {

    private final GameServerRepository gameServerRepository;
    private final EngineManager engineManager;
    @Getter private final EngineType engineType;

    public GameServerService(
            EngineProperties engineProperties, GameServerRepository gameServerRepository) {
        this.engineType = engineProperties.selected();
        this.gameServerRepository = gameServerRepository;

        this.engineManager =
                switch (engineType) {
                    case DOCKER -> {
                        if (engineProperties.docker() == null) {
                            throw new IllegalStateException(
                                    "Engine selected as DOCKER, but docker configuration is missing");
                        }
                        yield new DockerEngineManager(engineProperties.docker());
                    }
                    case KUBERNETES -> {
                        if (engineProperties.kubernetes() == null) {
                            throw new IllegalStateException(
                                    "Engine selected as KUBERNETES, but kubernetes configuration is missing");
                        }
                        yield new KubernetesEngineManager(engineProperties.kubernetes());
                    }

                    default -> throw new IllegalArgumentException(
                            "Unsupported engine type: " + engineType);
                };

        log.info("GameServerService initialized with engine '{}'", engineType);
    }

    @Transactional
    public List<Integer> startServer(String serviceName) {
        GameServerConfigurationEntity config =
                gameServerRepository.findById(serviceName).orElseThrow();

        log.info("Starting service '{}' on engine '{}'", serviceName, engineType);
        return engineManager.start(config);
    }

    public void stopServer(String serviceName) {
        GameServerConfigurationEntity config =
                gameServerRepository.findById(serviceName).orElseThrow();

        log.info("Stopping service '{}' on engine '{}'", serviceName, engineType);
        engineManager.stop(config);
    }

    public String getStatus(String serviceName) {
        GameServerConfigurationEntity config =
                gameServerRepository.findById(serviceName).orElseThrow();
        return engineManager.status(config);
    }
}
