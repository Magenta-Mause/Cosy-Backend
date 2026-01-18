package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.loki.LokiLogQuery;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.external.loki.LokiQueryService;
import com.magentamause.cosybackend.websockets.GameServerLogWebsocketPublisher;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerLogService {

    private final LokiQueryService lokiQueryService;
    private final GameServerLogWebsocketPublisher gameServerLogWebsocketPublisher;
    private static final Pattern LOG_ERROR_DETECTION_REGEX =
            Pattern.compile("\\[error\\]", Pattern.CASE_INSENSITIVE);

    public List<GameServerLogMessageEntity> getLogsForServer(
            String serverId, int limit, TemporalAmount since) {
        return lokiQueryService.queryLogs(
                LokiLogQuery.builder().gameServerUuid(serverId).limit(limit).build(), since);
    }

    public void saveGameServerLog(GameServerLogMessageEntity logEntity) {
        if (logEntity.getLevel() == GameServerLogMessageEntity.LogLevel.INFO
                && LOG_ERROR_DETECTION_REGEX.matcher(logEntity.getMessage()).find()) {
            logEntity.setLevel(GameServerLogMessageEntity.LogLevel.ERROR);
        }
        lokiQueryService.saveGameServerLog(logEntity);
        gameServerLogWebsocketPublisher.publishLog(logEntity.getGameServerUuid(), logEntity);
    }
}
