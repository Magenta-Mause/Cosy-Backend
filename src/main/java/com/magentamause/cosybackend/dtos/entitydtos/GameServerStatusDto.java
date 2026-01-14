package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.magentamause.cosybackend.entities.GameServerEntity;
import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class GameServerStatusDto {
    GameServerEntity.GameServerStatus status;
}
