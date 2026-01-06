package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class GameServerStatusDto {
    GameServerStatus status;

    // low level status as reported by engine
    String phase;

    // Documents high-level engine-agnostic status
    public enum GameServerStatus {
        Found,
        NotFound,
        Unknown
    }
}
