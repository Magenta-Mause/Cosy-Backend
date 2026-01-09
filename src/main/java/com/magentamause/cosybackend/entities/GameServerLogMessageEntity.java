package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.netty.handler.logging.LogLevel;
import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.time.LocalDateTime;

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
