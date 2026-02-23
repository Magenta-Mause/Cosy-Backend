package com.magentamause.cosybackend.controllers.gameserver.configurations;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerAccessGroupDto;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroupEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerAccessGroupService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerAccessGroupsController {
    GameServerAccessGroupService gameServerAccessGroupService;

    @PostMapping("/{uuid}/access-groups")
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<GameServerAccessGroupDto> createGameServerAccessGroup(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody AccessGroupCreationDto creationDto) {
        return ResponseEntity.ok(
                gameServerAccessGroupService.createAccessGroup(uuid, creationDto).toDto());
    }

    @DeleteMapping("/{uuid}/access-groups/{access_group_uuid}")
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<Void> deleteGameServerAccessGroup(
            @PathVariable @ResourceId String uuid,
            @PathVariable("access_group_uuid") String accessGroupUuid) {
        gameServerAccessGroupService.deleteAccessGroup(uuid, accessGroupUuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/access-groups/{access_group_uuid}")
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<List<GameServerAccessGroupDto>> updateGameServerAccessGroups(
            @PathVariable @ResourceId String uuid,
            @PathVariable("access_group_uuid") String accessGroupUuid,
            @RequestBody @Valid AccessGroupUpdateDto updateDto) {
        return ResponseEntity.ok(
                gameServerAccessGroupService
                        .updateAccessGroup(uuid, accessGroupUuid, updateDto)
                        .stream()
                        .map(GameServerAccessGroupEntity::toDto)
                        .toList());
    }
}
