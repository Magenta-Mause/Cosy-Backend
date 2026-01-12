package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.loki.LokiLogQuery;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.external.loki.LokiQueryService;

import java.time.temporal.TemporalAmount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameServerLogService {

    private final LokiQueryService lokiQueryService;

    public List<GameServerLogMessageEntity> getLogsForServer(String serverId, int limit, TemporalAmount since) {
        return lokiQueryService.queryLogs(
                LokiLogQuery.builder().gameServerUuid(serverId).limit(limit).build(), since);
    }

    public void saveGameServerLog(GameServerLogMessageEntity log) {
        lokiQueryService.saveGameServerLog(log);
    }
}
