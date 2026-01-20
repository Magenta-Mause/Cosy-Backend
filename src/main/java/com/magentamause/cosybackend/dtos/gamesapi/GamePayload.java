package com.magentamause.cosybackend.dtos.gamesapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GamePayload {
    @JsonProperty("id")
    private int externalGameId;

    @NotBlank
    private String name;

    @JsonProperty("hero_url")
    private String heroUrl;

    @JsonProperty("logo_url")
    private String logoUrl;

    public static GameDto toDto(GamePayload payload) {
        return GameDto.builder()
                .name(payload.getName())
                .heroUrl(payload.getHeroUrl())
                .logoUrl(payload.getLogoUrl())
                .externalGameId(payload.getExternalGameId())
                .build();
    }

    public GameDto toDto() {
        return toDto(this);
    }
}
