package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerLayoutsApi;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.PublicDashboardUpdateDto;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.entities.layout.PrivateDashboardLayout;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerConfigurationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameServerLayoutsController implements GameServerLayoutsApi {
    private final GameServerConfigurationService gameServerConfigurationService;

    @Override
    @NeedsValidation(Operation.GAME_SERVER_METRIC_CONFIG_CHANGE)
    public ResponseEntity<Void> updateMetricLayout(
            @ResourceId String uuid,
            List<MetricLayout> metricLayout) {
        gameServerConfigurationService.updateMetricLayout(uuid, metricLayout);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_PRIVATE_DASHBOARD_CONFIG_CHANGE)
    public ResponseEntity<Void> updatePrivateDashboard(
            @ResourceId String uuid,
            List<PrivateDashboardLayout> privateDashboardLayout) {
        gameServerConfigurationService.updatePrivateDashboardLayout(uuid, privateDashboardLayout);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_PUBLIC_DASHBOARD_CONFIG_CHANGE)
    public ResponseEntity<Void> updatePublicDashboardLayout(
            @ResourceId String uuid,
            PublicDashboardUpdateDto updateDto) {
        gameServerConfigurationService.updatePublicDashboardLayout(uuid, updateDto);
        return ResponseEntity.ok().build();
    }
}
