package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.netty.handler.logging.LogLevel;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerLogMessageEntity {
    private String uuid;
    private String gameServerUuid;
    private String message;
    private LogLevel level;
    private LocalDateTime timestamp;
}
