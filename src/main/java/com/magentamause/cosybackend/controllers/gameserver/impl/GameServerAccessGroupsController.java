package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerAccessGroupsApi;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerAccessGroupDto;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroupEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerAccessGroupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameServerAccessGroupsController implements GameServerAccessGroupsApi {
    private final GameServerAccessGroupService gameServerAccessGroupService;

    @Override
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<GameServerAccessGroupDto> createGameServerAccessGroup(
            @ResourceId String uuid,
            AccessGroupCreationDto creationDto) {
        return ResponseEntity.ok(
                gameServerAccessGroupService.createAccessGroup(uuid, creationDto).toDto());
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<Void> deleteGameServerAccessGroup(
            @ResourceId String uuid,
            String accessGroupUuid) {
        gameServerAccessGroupService.deleteAccessGroup(uuid, accessGroupUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<List<GameServerAccessGroupDto>> updateGameServerAccessGroups(
            @ResourceId String uuid,
            String accessGroupUuid,
            AccessGroupUpdateDto updateDto) {
        return ResponseEntity.ok(
                gameServerAccessGroupService
                        .updateAccessGroup(uuid, accessGroupUuid, updateDto)
                        .stream()
                        .map(GameServerAccessGroupEntity::toDto)
                        .toList());
    }
}
