package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerLogApi;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameServerLogController implements GameServerLogApi {

    private final GameServerLogService gameServerLogService;

    @Override
    @NeedsValidation(value = Operation.GAME_SERVER_LOG_READ, allowUnauthorized = true)
    public ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @ResourceId String gameServerUuid, int limit, int sinceHours) {
        return ResponseEntity.ok()
                .body(
                        gameServerLogService.getLogsForServer(
                                gameServerUuid, limit, Duration.ofHours(sinceHours)));
    }
}
