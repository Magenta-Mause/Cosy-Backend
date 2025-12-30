package com.magentamause.cosybackend.dtos.gamesapi;

import com.magentamause.cosybackend.dtos.entitydtos.GameDto;
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
        private List<GameDto> games;
    }
}
