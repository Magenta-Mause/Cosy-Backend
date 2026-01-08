package com.magentamause.cosybackend.dtos.entitydtos;

import io.netty.handler.logging.LogLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GameLogMessage {
	private String message;
	private LogLevel level;
	private LocalDateTime timestamp;
}
