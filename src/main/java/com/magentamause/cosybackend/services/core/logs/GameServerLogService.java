package com.magentamause.cosybackend.services.core.logs;

import com.magentamause.cosybackend.dtos.loki.LokiLogQuery;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
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

    public GameServerLogMessageEntity.LogLevel detectErrorLogLevel(String message) {
        return LOG_ERROR_DETECTION_REGEX.matcher(message).find()
                ? GameServerLogMessageEntity.LogLevel.ERROR
                : GameServerLogMessageEntity.LogLevel.INFO;
    }

    public GameServerLogMessageEntity publishAndSaveLog(
            GameServerLogMessageEntity logEntity, boolean parseErrorLogLevel) {
        GameServerLogMessageEntity copy = logEntity.copy();
        if (parseErrorLogLevel
                && logEntity.getMessage() != null
                && detectErrorLogLevel(logEntity.getMessage())
                        == GameServerLogMessageEntity.LogLevel.ERROR) {
            copy.setLevel(GameServerLogMessageEntity.LogLevel.ERROR);
        }
        lokiQueryService.saveGameServerLog(copy);
        gameServerLogWebsocketPublisher.publishLog(copy.getGameServerUuid(), logEntity);
        return copy;
    }

    public GameServerLogMessageEntity publishAndSaveLog(
            GameServerEntity gameServer,
            GameServerLogMessageEntity.LogLevel logLevel,
            String message,
            boolean parseErrorLogLevel) {
        GameServerLogMessageEntity logEntity =
                GameServerLogMessageEntity.of(gameServer.getUuid(), message, logLevel);

        return publishAndSaveLog(logEntity, parseErrorLogLevel);
    }
}
