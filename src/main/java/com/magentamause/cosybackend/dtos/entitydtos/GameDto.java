package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import tools.jackson.databind.PropertyNamingStrategy;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameDto {
    @NotNull private String gameUuid;
    @NotBlank private String name;

    private int externalGameId;

    private String heroUrl;

    private String logoUrl;

    public static GameDto fromEntity(GameEntity gameEntity) {
        return GameDto.builder()
                .gameUuid(gameEntity.getUuid())
                .name(gameEntity.getName())
                .heroUrl(gameEntity.getHeroUrl())
                .logoUrl(gameEntity.getLogoUrl())
                .externalGameId(gameEntity.getExternalGameId())
                .build();
    }
}
