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

    public enum LogLevel {
        INFO,
        WARNING,
        ERROR
    }
}
