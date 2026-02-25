package com.magentamause.cosybackend.controllers.gameserver.configurations;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.PublicDashboardUpdateDto;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.entities.layout.PrivateDashboardLayout;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerLayoutsController {
    private final GameServerConfigurationService gameServerConfigurationService;

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

    @PatchMapping("/{uuid}/layout/public-dashboard")
    @NeedsValidation(Operation.GAME_SERVER_PUBLIC_DASHBOARD_CONFIG_CHANGE)
    public ResponseEntity<Void> updatePublicDashboardLayout(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody PublicDashboardUpdateDto updateDto) {
        gameServerConfigurationService.updatePublicDashboardLayout(uuid, updateDto);
        return ResponseEntity.ok().build();
    }
}
