package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.services.GameServerService;
import com.magentamause.cosybackend.services.SecurityContextService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server-configurations")
public class GameServerConfigurationController {

    private final GameServerService gameServerService;
    private final SecurityContextService securityContextService;

    @GetMapping
    public ResponseEntity<List<GameServerDto>> getAllGameServers() {
        List<GameServerDto> dtos =
                gameServerService.getAllGameServers().stream()
                        .map(GameServerEntity::toDto)
                        .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<GameServerDto> getGameServerById(@PathVariable String uuid) {
        GameServerEntity entity =
                gameServerService.getGameServerById(uuid);
        return ResponseEntity.ok(entity.toDto());
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteGameServerById(@PathVariable String uuid) {
        gameServerService.deleteGameServerById(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<GameServerDto> createGameServer(
            @Valid @RequestBody GameServerCreationDto gameServerCreationDto) {
        UserEntity user = securityContextService.getUser();

        GameServerEntity createdGameServer =
                GameServerEntity.builder()
                        .owner(user)
                        .gameUuid(gameServerCreationDto.getGameUuid())
                        .serverName(gameServerCreationDto.getServerName())
                        .template(gameServerCreationDto.getTemplate())
                        .dockerImageName(gameServerCreationDto.getDockerImageName())
                        .dockerImageTag(gameServerCreationDto.getDockerImageTag())
                        .dockerExecutionCommand(gameServerCreationDto.getExecutionCommand())
                        .environmentVariables(gameServerCreationDto.getEnvironmentVariables())
                        .volumeMounts(
                                gameServerCreationDto.getVolumeMounts() != null
                                        ? gameServerCreationDto.getVolumeMounts().stream()
                                        .map(VolumeMountConfiguration::fromDto)
                                        .toList()
                                        : null)
                        .portMappings(
                                gameServerCreationDto.getPortMappings() != null
                                        ? gameServerCreationDto.getPortMappings()
                                        : List.of())
                        .build();

        gameServerService.saveGameServer(createdGameServer);
        return ResponseEntity.status(201).body(createdGameServer.toDto());
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<GameServerDto> updateGameServer(
            @PathVariable String uuid,
            @Valid @RequestBody GameServerUpdateDto updateDto
    ) {
        log.info("Received request to update the game server with id {}", uuid);

        UserEntity user = securityContextService.getUser();

        GameServerEntity updated =
                gameServerService.updateGameServerConfiguration(uuid, updateDto, user);

        return ResponseEntity.ok(updated.toDto());
    }
}
