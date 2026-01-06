package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.EngineType;
import com.magentamause.cosybackend.engine.config.EngineProperties;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GameServerService {

    private final GameServerRepository gameServerRepository;
    private final EngineManager engineManager;
    private final EngineType engineType;
    private final Set<String> startingServers = ConcurrentHashMap.newKeySet();

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
        if (!startingServers.add(serviceName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Server '" + serviceName + "' is already starting");
        }

        try {
            GameServerConfigurationEntity config =
                    gameServerRepository
                            .findById(serviceName)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Server '" + serviceName + "' not found"));

            return engineManager.start(config);
        }
        finally {
            startingServers.remove(serviceName);
        }
    }

    public void stopServer(String serviceName) {
        GameServerConfigurationEntity config =
                gameServerRepository
                        .findById(serviceName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Server '" + serviceName + "' not found"));

        try {
            engineManager.stop(config);
        }
        catch (ServerAlreadyStoppedException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    public String getStatus(String serviceName) {
        return engineManager.status(gameServerRepository.findById(serviceName).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Server '" + serviceName + "' not found")));
    }
}
