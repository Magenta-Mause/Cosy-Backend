package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magentamause.cosybackend.entities.GameEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameDto {
    @NotNull private String gameUuid;
    @NotBlank private String name;

    @JsonProperty("hero_url")
    private String heroUrl;

    @JsonProperty("logo_url")
    private String logoUrl;

    public static GameDto fromEntity(GameEntity gameEntity) {
        return GameDto.builder()
                .gameUuid(gameEntity.getUuid())
                .name(gameEntity.getName())
                .heroUrl(gameEntity.getHeroUrl())
                .logoUrl(gameEntity.getLogoUrl())
                .build();
    }
}
