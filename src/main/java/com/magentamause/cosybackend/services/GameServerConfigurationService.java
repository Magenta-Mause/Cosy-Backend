package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.repositories.GameServerRepository;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerConfigurationService {

    private final GameServerRepository gameServerRepository;

    public List<GameServerConfigurationEntity> getAllGameServers() {
        return gameServerRepository.findAll();
    }

    public GameServerConfigurationEntity getGameServerById(String uuid) {
        return gameServerRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
    }

    public GameServerConfigurationEntity saveGameServer(GameServerConfigurationEntity entity) {
        entity.setUuid(null);
        entity.setStatus(GameServerConfigurationEntity.GameServerStatus.STOPPED);
        log.info("Saving game server {}", entity);
        return gameServerRepository.save(entity);
    }

    public void deleteGameServerById(String uuid) {
        gameServerRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
        gameServerRepository.deleteById(uuid);
    }

    public GameServerConfigurationEntity updateGameServerConfiguration(
            String uuid, GameServerUpdateDto dto, UserEntity owner) {

        GameServerConfigurationEntity gameServer = gameServerRepository
                .findById(uuid)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Game server with uuid " + uuid + " not found"));

        if (!gameServer.getOwner().getUuid().equals(owner.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        gameServer.setGameUuid(dto.getGameUuid());
        gameServer.setServerName(dto.getServerName());
        gameServer.setDockerImageName(dto.getDockerImageName());
        gameServer.setDockerImageTag(dto.getDockerImageTag());
        gameServer.setDockerExecutionCommand(dto.getExecutionCommand());

        if (gameServer.getPortMappings() == null) {
            gameServer.setPortMappings(new ArrayList<>());
        }
        gameServer.getPortMappings().clear();
        gameServer.getPortMappings().addAll(dto.getPortMappings());

        if (gameServer.getEnvironmentVariables() == null) {
            gameServer.setEnvironmentVariables(new ArrayList<>());
        }
        gameServer.getEnvironmentVariables().clear();
        gameServer.getEnvironmentVariables().addAll(dto.getEnvironmentVariables());

        if (gameServer.getVolumeMounts() == null) {
            gameServer.setVolumeMounts(new ArrayList<>());
        }
        gameServer.getVolumeMounts().clear();
        gameServer.getVolumeMounts().addAll(
                dto.getVolumeMounts().stream()
                        .map(VolumeMountConfiguration::fromDto)
                        .toList()
        );

        return gameServerRepository.save(gameServer);
    }
}
