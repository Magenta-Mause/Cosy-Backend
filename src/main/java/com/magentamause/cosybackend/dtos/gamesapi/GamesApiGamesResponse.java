package com.magentamause.cosybackend.dtos.gamesapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamesApiGamesResponse {
    private boolean success;
    private long timestamp;
    private DataPayload data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPayload {
        private List<GamesPayload> games;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GamesPayload {
            @JsonProperty("id")
            private int externalGameId;

            @NotBlank private String name;

            @JsonProperty("hero_url")
            private String heroUrl;

            @JsonProperty("logo_url")
            private String logoUrl;

            public static GameDto toDto(GamesPayload payload) {
                return GameDto.builder()
                        .name(payload.getName())
                        .heroUrl(payload.getHeroUrl())
                        .logoUrl(payload.getLogoUrl())
                        .build();
            }
        }
    }
}
