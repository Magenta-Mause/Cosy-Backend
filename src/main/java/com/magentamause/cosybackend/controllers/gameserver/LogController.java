package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server/{gameServerUuid}/logs")
public class LogController {

    private final GameServerLogService gameServerLogService;

    @GetMapping
    @NeedsValidation(Operation.GAME_SERVER_LOG_READ)
    public ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @ResourceId @PathVariable String gameServerUuid,
            @RequestParam(defaultValue = "500", required = false) @Min(1) @Max(2000) int limit,
            @RequestParam(defaultValue = "5", required = false) @Min(1) @Max(400) int sinceHours) {
        return ResponseEntity.ok()
                .body(
                        gameServerLogService.getLogsForServer(
                                gameServerUuid, limit, Duration.ofHours(sinceHours)));
    }
}
