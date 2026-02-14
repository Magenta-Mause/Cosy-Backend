package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerAccessGroupDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerConfigurationController {
    private final GameServerConfigurationService gameServerConfigurationService;

    @PatchMapping("{uuid}/layout/metric")
    @NeedsValidation(Operation.GAME_SERVER_METRIC_CONFIG_CHANGE)
    public ResponseEntity<Void> updateMetricLayout(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody List<MetricLayout> metricLayout) {
        gameServerConfigurationService.updateMetricLayout(uuid, metricLayout);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{uuid}/rcon-configuration")
    @NeedsValidation(Operation.GAME_SERVER_RCON_CONFIG_CHANGE)
    public ResponseEntity<GameServerDto> updateRconConfiguration(
            @PathVariable @ResourceId String uuid,
            @RequestBody @Valid RCONConfiguration updateDto) {
        GameServerEntity gameServer =
                gameServerConfigurationService.updateRconConfig(uuid, updateDto);
        return ResponseEntity.ok(gameServer.toDto());
    }

    @PostMapping("/{game_server_uuid}/access-groups")
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<GameServerAccessGroupDto> createGameServerAccessGroup(
            @PathVariable("game_server_uuid") @ResourceId String gameServerUuid,
            @Valid @RequestBody AccessGroupCreationDto creationDto) {
        return ResponseEntity.ok(
                gameServerConfigurationService.createAccessGroup(gameServerUuid, creationDto).toDto());
    }

    @DeleteMapping("/{game_server_uuid}/access-groups/{access_group_uuid}")
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<Void> deleteGameServerAccessGroup(
            @PathVariable("game_server_uuid") @ResourceId String gameServerUuid,
            @PathVariable("access_group_uuid") String accessGroupUuid) {
        gameServerConfigurationService.deleteAccessGroup(gameServerUuid, accessGroupUuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{game_server_uuid}/access-groups/{access_group_uuid}")
    @NeedsValidation(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public ResponseEntity<List<GameServerAccessGroupDto>> updateGameServerAccessGroups(
            @PathVariable("game_server_uuid") @ResourceId String gameServerUuid,
            @PathVariable("access_group_uuid") String accessGroupUuid,
            @RequestBody @Valid AccessGroupUpdateDto updateDto) {
        return ResponseEntity.ok(
                gameServerConfigurationService.updateAccessGroup(
                                gameServerUuid, accessGroupUuid, updateDto)
                        .stream().map(GameServerAccessGroup::toDto).toList());
    }
}
