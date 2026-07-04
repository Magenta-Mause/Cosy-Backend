package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerRootApi;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameServerRootController implements GameServerRootApi {

    private final GameServerService gameServerService;
    private final SecurityContextService securityContextService;

    @Override
    public ResponseEntity<List<GameServerDto>> getAllGameServers() {
        UserEntity user = securityContextService.getUser();
        if (user == null) {
            List<GameServerDto> dtos =
                    gameServerService.getPubliclyEvaluableGameServer().stream()
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

    @Override
    @NeedsValidation(Operation.GAME_SERVER_GET)
    public ResponseEntity<GameServerDto> getGameServerById(@ResourceId String uuid) {
        GameServerEntity entity = gameServerService.getOrThrow(uuid);
        return ResponseEntity.ok(entity.toDto(securityContextService.getUser()));
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_DELETE)
    public ResponseEntity<Void> deleteGameServerById(@ResourceId String uuid) {
        gameServerService.deleteGameServerById(uuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_CREATE)
    public ResponseEntity<GameServerDto> createGameServer(GameServerCreationDto gameServer) {
        log.info("Creating game server {}", gameServer);
        UserEntity user = securityContextService.getUser();

        GameServerEntity createdGameServer = gameServerService.createGameServer(user, gameServer);
        return ResponseEntity.status(201).body(createdGameServer.toDto(user));
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_UPDATE)
    public ResponseEntity<GameServerDto> updateGameServer(
            @ResourceId String uuid, GameServerUpdateDto updateDto) {
        log.info("Updating game server {} with {}", uuid, updateDto);
        UserEntity user = securityContextService.getUser();

        GameServerEntity updated =
                gameServerService.updateGameServerConfiguration(user, uuid, updateDto);
        return ResponseEntity.ok(updated.toDto(user));
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_GET)
    public ResponseEntity<GameServerDto.GameServerStatus> getServiceInfo(@ResourceId String uuid) {
        return ResponseEntity.ok(gameServerService.getStatus(uuid));
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_START_STOP)
    public ResponseEntity<Void> startService(@ResourceId String uuid) {
        gameServerService.startServer(uuid);
        return ResponseEntity.accepted().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_START_STOP)
    public ResponseEntity<Void> stopService(@ResourceId String uuid) {
        gameServerService.stopServer(uuid);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_SEND_COMMAND)
    public ResponseEntity<Void> sendCommand(@ResourceId String uuid, SendCommandDto command) {
        gameServerService.sendCommand(uuid, command.getCommand());
        return ResponseEntity.noContent().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_TRANSFER_OWNERSHIP)
    public ResponseEntity<GameServerDto> transferOwnership(
            @ResourceId String uuid, TransferOwnershipDto transferOwnershipDto) {
        GameServerEntity updated =
                gameServerService.transferGameServerOwnership(uuid, transferOwnershipDto);
        return ResponseEntity.ok(updated.toDto(securityContextService.getUser()));
    }
}
