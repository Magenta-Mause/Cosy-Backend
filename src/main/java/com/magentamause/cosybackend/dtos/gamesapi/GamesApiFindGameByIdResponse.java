package com.magentamause.cosybackend.dtos.gamesapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamesApiFindGameByIdResponse {
    private boolean success;
    private long timestamp;
    private GamePayload data;
}
