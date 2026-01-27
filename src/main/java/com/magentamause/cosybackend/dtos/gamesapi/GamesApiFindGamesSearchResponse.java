package com.magentamause.cosybackend.dtos.gamesapi;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamesApiFindGamesSearchResponse {
    private boolean success;
    private long timestamp;
    private DataPayload data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPayload {
        private List<GamePayload> games;
    }
}
