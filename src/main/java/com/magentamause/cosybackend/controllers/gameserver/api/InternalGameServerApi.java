package com.magentamause.cosybackend.controllers.gameserver.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Internal Game Server", description = "Internal endpoints for game server agents")
@RequestMapping("/internal/game-server")
public interface InternalGameServerApi {

    @Operation(summary = "Update a custom metric for a game server")
    @ApiResponse(responseCode = "200", description = "Metric updated")
    @PutMapping("/custom-metric/{uuid}")
    ResponseEntity<Map<String, Object>> updateCustomMetric(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestHeader("Authorization") String secret,
            @RequestBody Map<String, Object> value);

    @Operation(summary = "Test connectivity to a game server")
    @ApiResponse(responseCode = "200", description = "Connection status returned")
    @GetMapping("/test-connection/{uuid}")
    ResponseEntity<Boolean> checkConnection(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestHeader("Authorization") String secret);
}
