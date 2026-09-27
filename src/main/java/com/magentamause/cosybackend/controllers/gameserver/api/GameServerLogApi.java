package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Game Server Logs", description = "Game server log retrieval")
@RequestMapping("/game-server/{gameServerUuid}/logs")
public interface GameServerLogApi {

    @Operation(
            summary = "Get game server logs",
            description =
                    "Returns the newest `limit` log lines within [start, end). If exactly `limit`"
                            + " lines are returned there may be older ones; request them with"
                            + " `end` set to the oldest returned timestamp. The range is clamped to"
                            + " the log retention period.")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    @ApiResponse(responseCode = "400", description = "Invalid or too large time range")
    @GetMapping
    ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @Parameter(description = "Game server UUID") @PathVariable String gameServerUuid,
            @Parameter(description = "Range start (inclusive), defaults to end minus 5 hours")
                    @RequestParam(required = false)
                    Instant start,
            @Parameter(description = "Range end (exclusive), defaults to now")
                    @RequestParam(required = false)
                    Instant end,
            @RequestParam(defaultValue = "500", required = false) @Min(1) @Max(2000) int limit);
}
