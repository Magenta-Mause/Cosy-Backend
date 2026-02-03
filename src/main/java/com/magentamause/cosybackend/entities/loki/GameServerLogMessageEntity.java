package com.magentamause.cosybackend.entities.loki;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerLogMessageEntity {
    private String gameServerUuid;
    private String message;
    private LogLevel level;
    private Instant timestamp;

    public static GameServerLogMessageEntity of(
            String gameServerUuid, String message, LogLevel level) {
        return GameServerLogMessageEntity.builder()
                .gameServerUuid(gameServerUuid)
                .message(message)
                .level(level)
                .timestamp(Instant.now())
                .build();
    }

    public GameServerLogMessageEntity copy() {
        return GameServerLogMessageEntity.builder()
                .gameServerUuid(gameServerUuid)
                .message(message)
                .level(level)
                .timestamp(timestamp)
                .build();
    }

    public enum LogLevel {
        INFO,
        ERROR,
        INPUT,
        COSY_INFO,
        COSY_DEBUG,
        COSY_ERROR
    }
}
