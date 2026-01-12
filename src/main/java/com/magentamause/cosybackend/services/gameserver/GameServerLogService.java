package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.loki.LokiLogQuery;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.services.external.loki.LokiQueryService;

import java.time.temporal.TemporalAmount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerLogService {

    private final LokiQueryService lokiQueryService;

    public List<GameServerLogMessageEntity> getLogsForServer(String serverId, int limit, TemporalAmount since) {
        return lokiQueryService.queryLogs(
                LokiLogQuery.builder().gameServerUuid(serverId).limit(limit).build(), since);
    }

    public void saveGameServerLog(GameServerLogMessageEntity logEntity) {
        try {
            lokiQueryService.saveGameServerLog(logEntity);
        } catch (NullPointerException e) {
            log.info("Error on log: {}", logEntity);
        }
    }
}
