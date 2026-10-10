package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerLogApi;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.logs.GameServerLogService;
import com.magentamause.cosybackend.services.core.timerange.TimeRange;
import com.magentamause.cosybackend.services.core.timerange.TimeRangeService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameServerLogController implements GameServerLogApi {

    private final GameServerLogService gameServerLogService;
    private final TimeRangeService timeRangeService;

    @Override
    @NeedsValidation(value = Operation.GAME_SERVER_LOG_READ, allowUnauthorized = true)
    public ResponseEntity<List<GameServerLogMessageEntity>> getLogs(
            @ResourceId String gameServerUuid, Instant start, Instant end, int limit) {
        TimeRange range =
                timeRangeService.resolveForGameServer(
                        gameServerUuid, start, end, GameServerAccessPermission.READ_SERVER_LOGS);
        return ResponseEntity.ok()
                .body(gameServerLogService.getLogsForServer(gameServerUuid, limit, range));
    }
}
