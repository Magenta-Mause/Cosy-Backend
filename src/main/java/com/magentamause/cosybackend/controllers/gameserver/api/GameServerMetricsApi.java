package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Game Server Metrics", description = "Game server metrics queries")
@RequestMapping("/game-server/{gameServerUuid}/metrics")
public interface GameServerMetricsApi {

    @Operation(summary = "Query metrics for a game server")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics returned"),
        @ApiResponse(responseCode = "400", description = "Invalid time range")
    })
    @GetMapping
    ResponseEntity<List<MetricPointDto>> getMetrics(
            @Parameter(description = "Game server UUID") @PathVariable String gameServerUuid,
            @RequestParam(required = false) Instant end,
            @RequestParam(required = false) Instant start,
            @RequestParam(defaultValue = "100") int pointCount);

    @Operation(summary = "Query public metrics for a publicly evaluable game server")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics returned"),
        @ApiResponse(responseCode = "400", description = "Invalid time range"),
        @ApiResponse(responseCode = "404", description = "Game server not publicly evaluable")
    })
    @GetMapping("/public")
    ResponseEntity<List<MetricPointDto>> getPublicEvaluableMetrics(
            @Parameter(description = "Game server UUID") @PathVariable String gameServerUuid,
            @RequestParam(required = false) Instant end,
            @RequestParam(required = false) Instant start,
            @RequestParam(defaultValue = "100") int pointCount);
}
