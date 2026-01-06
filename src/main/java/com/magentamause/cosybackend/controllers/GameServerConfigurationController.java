package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.GameServerConfigurationService;
import com.magentamause.cosybackend.services.SecurityContextService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server-configurations")
public class GameServerConfigurationController {

    private final GameServerConfigurationService gameServerConfigurationService;
    private final SecurityContextService securityContextService;

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
    public ResponseEntity<List<GameServerDto>> getAllGameServers() {
        List<GameServerDto> dtos =
                gameServerConfigurationService.getAllGameServers().stream()
                        .map(GameServerEntity::toDto)
                        .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{uuid}")
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
    public ResponseEntity<GameServerDto> getGameServerById(@PathVariable @ResourceId String uuid) {
        GameServerEntity entity =
                gameServerConfigurationService.getGameServerById(uuid);
        return ResponseEntity.ok(entity.toDto());
    }

    @DeleteMapping("/{uuid}")
    @RequireAccess(action = Action.DELETE, resource = Resource.GAME_SERVER)
    public ResponseEntity<Void> deleteGameServerById(@PathVariable @ResourceId String uuid) {
        gameServerConfigurationService.deleteGameServerById(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @RequireAccess(action = Action.CREATE, resource = Resource.GAME_SERVER)
    public ResponseEntity<GameServerDto> createGameServer(
            @Valid @RequestBody GameServerCreationDto gameServerCreationDto) {
        UserEntity user = securityContextService.getUser();

        GameServerEntity createdGameServer = gameServerCreationDto.toEntity();
        createdGameServer.setOwner(user);

        gameServerConfigurationService.saveGameServer(createdGameServer);
        return ResponseEntity.status(201).body(createdGameServer.toDto());
    }
}
