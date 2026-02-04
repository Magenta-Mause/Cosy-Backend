package com.magentamause.cosybackend.services.external.loki;

import com.magentamause.cosybackend.dtos.loki.LokiQueryResponse;
import com.magentamause.cosybackend.dtos.loki.LokiStreamResult;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LokiMapper {

    public static List<GameServerLogMessageEntity> toEntities(LokiQueryResponse response) {

        if (response == null || response.data() == null) {
            return List.of();
        }

        Stream<LokiStreamResult> resultStream = response.data().result().stream();

        return resultStream.flatMap(LokiMapper::parseResult).toList();
    }

    private static Stream<GameServerLogMessageEntity> parseResult(LokiStreamResult result) {
        return result.values().stream().map(value -> parseToLogEntity(result, value));
    }

    private static GameServerLogMessageEntity parseToLogEntity(
            LokiStreamResult result, List<String> value) {
        String level = result.stream().get("level");
        String serverUuid = result.stream().get("server_uuid");
        String message = value.get(1);
        return GameServerLogMessageEntity.builder()
                .message(message)
                .gameServerUuid(serverUuid)
                .timestamp(parseTimestamp(value.get(0)))
                .level(parseLogLevel(level))
                .build();
    }

    private static GameServerLogMessageEntity.LogLevel parseLogLevel(String logLevel) {
        if (logLevel == null) {
            return GameServerLogMessageEntity.LogLevel.COSY_DEBUG;
        }
        return switch (logLevel) {
            case "ERROR" -> GameServerLogMessageEntity.LogLevel.ERROR;
            case "INFO" -> GameServerLogMessageEntity.LogLevel.INFO;
            case "INPUT" -> GameServerLogMessageEntity.LogLevel.INPUT;
            case "COSY_INFO" -> GameServerLogMessageEntity.LogLevel.COSY_INFO;
            case "COSY_ERROR" -> GameServerLogMessageEntity.LogLevel.COSY_ERROR;
            case "COSY_DEBUG" -> GameServerLogMessageEntity.LogLevel.COSY_DEBUG;
            default -> GameServerLogMessageEntity.LogLevel.COSY_DEBUG;
        };
    }

    private static Instant parseTimestamp(String timestamp) {
        return Instant.ofEpochMilli(Long.parseLong(timestamp) / 1_000_000);
    }
}
