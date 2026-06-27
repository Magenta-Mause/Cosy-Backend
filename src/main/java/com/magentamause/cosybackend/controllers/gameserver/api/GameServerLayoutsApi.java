package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.PublicDashboardUpdateDto;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.entities.layout.PrivateDashboardLayout;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Game Server Layouts", description = "Dashboard and metric layout configuration")
@RequestMapping("/game-server")
public interface GameServerLayoutsApi {

    @Operation(summary = "Update metric layout for a game server")
    @ApiResponse(responseCode = "200", description = "Metric layout updated")
    @PatchMapping("/{uuid}/layout/metric")
    ResponseEntity<Void> updateMetricLayout(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody List<MetricLayout> metricLayout);

    @Operation(summary = "Update private dashboard layout for a game server")
    @ApiResponse(responseCode = "200", description = "Private dashboard layout updated")
    @PatchMapping("/{uuid}/layout/private-dashboard")
    ResponseEntity<Void> updatePrivateDashboard(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody List<PrivateDashboardLayout> privateDashboardLayout);

    @Operation(summary = "Update public dashboard layout for a game server")
    @ApiResponse(responseCode = "200", description = "Public dashboard layout updated")
    @PatchMapping("/{uuid}/layout/public-dashboard")
    ResponseEntity<Void> updatePublicDashboardLayout(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody PublicDashboardUpdateDto updateDto);
}
