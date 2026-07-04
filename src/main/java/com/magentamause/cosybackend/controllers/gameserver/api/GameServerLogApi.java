package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Game Server Logs", description = "Game server log retrieval")
@RequestMapping("/game-server/{gameServerUuid}/logs")
public interface GameServerLogApi {

    @Operation(summary = "Get game server logs")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    @GetMapping
    ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @Parameter(description = "Game server UUID") @PathVariable String gameServerUuid,
            @RequestParam(defaultValue = "500", required = false) @Min(1) @Max(2000) int limit,
            @RequestParam(defaultValue = "5", required = false) @Min(1) @Max(400) int sinceHours);
}
