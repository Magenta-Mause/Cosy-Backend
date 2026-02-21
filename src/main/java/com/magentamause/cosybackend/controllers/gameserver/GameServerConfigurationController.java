package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerAccessGroupDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroupEntity;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.entities.layout.PrivateDashboardLayout;
import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerAccessGroupService;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerConfigurationController {
    private final GameServerConfigurationService gameServerConfigurationService;
    private final GameServerAccessGroupService gameServerAccessGroupService;
    private final SecurityContextService securityContextService;

    @PatchMapping("/{uuid}/layout/metric")
    @NeedsValidation(Operation.GAME_SERVER_METRIC_CONFIG_CHANGE)
    public ResponseEntity<Void> updateMetricLayout(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody List<MetricLayout> metricLayout) {
        gameServerConfigurationService.updateMetricLayout(uuid, metricLayout);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{uuid}/layout/private-dashboard")
    @NeedsValidation(Operation.GAME_SERVER_PRIVATE_DASHBOARD_CONFIG_CHANGE)
    public ResponseEntity<Void> updatePrivateDashboard(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody List<PrivateDashboardLayout> privateDashboardLayout) {
        gameServerConfigurationService.updatePrivateDashboardLayout(uuid, privateDashboardLayout);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{uuid}/layout/public-dashboard/{is_public}")
    @NeedsValidation(Operation.GAME_SERVER_PUBLIC_DASHBOARD_CONFIG_CHANGE)
    public ResponseEntity<Void> updatePublicDashboardLayout(
            @PathVariable @ResourceId String uuid, @PathVariable("is_public") boolean isPublic,
            @Valid @RequestBody List<PublicDashboardLayout> publicDashboardLayouts) {
        gameServerConfigurationService.updatePublicDashboardLayout(uuid, publicDashboardLayouts, isPublic);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{uuid}/rcon-configuration")
    @NeedsValidation(Operation.GAME_SERVER_RCON_CONFIG_CHANGE)
    public ResponseEntity<GameServerDto> updateRconConfiguration(
            @PathVariable @ResourceId String uuid,
            @RequestBody @Valid RCONConfiguration updateDto) {
        GameServerEntity gameServer =
                gameServerConfigurationService.updateRconConfig(uuid, updateDto);
        return ResponseEntity.ok(gameServer.toDto(securityContextService.getUser()));
    }

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
