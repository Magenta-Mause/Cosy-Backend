package com.magentamause.cosybackend.dtos.loki;

import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import java.util.Set;
import lombok.Builder;

@Builder
public record LokiLogQuery(
        String gameServerUuid, Set<GameServerLogMessageEntity.LogLevel> levels, int limit) {}
