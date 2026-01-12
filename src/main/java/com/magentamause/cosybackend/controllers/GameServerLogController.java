package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.gameserver.GameServerLogService;
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
public class GameServerLogController {

    private final GameServerLogService gameServerLogService;

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER_LOG)
    public ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @ResourceId @PathVariable String gameServerUuid,
            @RequestParam(defaultValue = "100", required = false) @Min(0) @Max(500) int limit,
            @RequestParam(defaultValue = "5", required = false) @Min(1) @Max(400) int sinceHours) {
        return ResponseEntity.ok()
                .body(
                        gameServerLogService.getLogsForServer(
                                gameServerUuid, limit, Duration.ofHours(sinceHours)));
    }
}
