package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.TransferOwnershipDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.SendCommandDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerRootController {

    private final GameServerService gameServerService;
    private final SecurityContextService securityContextService;

    @GetMapping("/all")
    public ResponseEntity<List<GameServerDto>> getAllGameServers() {
        UserEntity user = securityContextService.getUser();
        if (user == null) {
            List<GameServerDto> dtos = gameServerService.getPubliclyEvaluableGameServer().stream()
                    .map(GameServerEntity::toPublicDto)
                    .toList();
            return ResponseEntity.ok(dtos);
        }

        List<GameServerDto> dtos =
                gameServerService.getGameServersVisibleToUser(user).stream()
                        .map(server -> server.toDto(user))
                        .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{uuid}")
    @NeedsValidation(Operation.GAME_SERVER_GET)
    public ResponseEntity<GameServerDto> getGameServerById(@PathVariable @ResourceId String uuid) {
        GameServerEntity entity = gameServerService.getOrThrow(uuid);
        return ResponseEntity.ok(entity.toDto(securityContextService.getUser()));
    }

    @DeleteMapping("/{uuid}")
    @NeedsValidation(Operation.GAME_SERVER_DELETE)
    public ResponseEntity<Void> deleteGameServerById(@PathVariable @ResourceId String uuid) {
        gameServerService.deleteGameServerById(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @NeedsValidation(Operation.GAME_SERVER_CREATE)
    public ResponseEntity<GameServerDto> createGameServer(
            @Valid @RequestBody GameServerCreationDto gameServer) {
        log.info("Creating game server {}", gameServer);
        UserEntity user = securityContextService.getUser();

        GameServerEntity createdGameServer = gameServerService.createGameServer(user, gameServer);
        return ResponseEntity.status(201).body(createdGameServer.toDto(user));
    }

    @PutMapping("/{uuid}")
    @NeedsValidation(Operation.GAME_SERVER_UPDATE)
    public ResponseEntity<GameServerDto> updateGameServer(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody GameServerUpdateDto updateDto) {
        log.info("Updating game server {} with {}", uuid, updateDto);

        GameServerEntity updated = gameServerService.updateGameServerConfiguration(uuid, updateDto);
        return ResponseEntity.ok(updated.toDto(securityContextService.getUser()));
    }

    @GetMapping("/{uuid}/status")
    @NeedsValidation(Operation.GAME_SERVER_GET)
    public ResponseEntity<GameServerDto.GameServerStatus> getServiceInfo(
            @PathVariable @ResourceId String uuid) {
        return ResponseEntity.ok(gameServerService.getStatus(uuid));
    }

    @PostMapping(value = "/{uuid}/start")
    @NeedsValidation(Operation.GAME_SERVER_START_STOP)
    public ResponseEntity<Void> startService(@PathVariable @ResourceId String uuid) {
        gameServerService.startServer(uuid, securityContextService.getUser());

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{uuid}/stop")
    @NeedsValidation(Operation.GAME_SERVER_START_STOP)
    public ResponseEntity<Void> stopService(@PathVariable @ResourceId String uuid) {
        gameServerService.stopServer(uuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{uuid}/send-command")
    @NeedsValidation(Operation.GAME_SERVER_SEND_COMMAND)
    public ResponseEntity<Void> sendCommand(
            @PathVariable @ResourceId String uuid, @RequestBody SendCommandDto command) {
        gameServerService.sendCommand(uuid, command.getCommand());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/transfer-ownership")
    @NeedsValidation(Operation.GAME_SERVER_TRANSFER_OWNERSHIP)
    public ResponseEntity<GameServerDto> transferOwnership(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody TransferOwnershipDto transferOwnershipDto) {
        GameServerEntity updated =
                gameServerService.transferGameServerOwnership(uuid, transferOwnershipDto);
        return ResponseEntity.ok(updated.toDto(securityContextService.getUser()));
    }
}
