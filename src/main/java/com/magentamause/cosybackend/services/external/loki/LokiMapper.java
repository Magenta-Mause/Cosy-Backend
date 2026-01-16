package com.magentamause.cosybackend.services.external.loki;

import com.magentamause.cosybackend.dtos.loki.LokiQueryResponse;
import com.magentamause.cosybackend.dtos.loki.LokiStreamResult;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LokiMapper {

    private static final Pattern ERROR_DETECTION_REGEX =
            Pattern.compile("\\[error]", Pattern.CASE_INSENSITIVE);

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
                .level(
                        "ERROR".equals(level)
                                ? GameServerLogMessageEntity.LogLevel.ERROR
                                : "INFO".equals(level)
                                        ? GameServerLogMessageEntity.LogLevel.INFO
                                        : GameServerLogMessageEntity.LogLevel.COSY_DEBUG)
                .build();
    }

    private static Instant parseTimestamp(String timestamp) {
        return Instant.ofEpochMilli(Long.parseLong(timestamp) / 1_000_000);
    }
}
