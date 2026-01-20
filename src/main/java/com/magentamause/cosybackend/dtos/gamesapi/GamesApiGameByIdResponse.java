package com.magentamause.cosybackend.dtos.gamesapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamesApiGameByIdResponse {
    private boolean success;
    private long timestamp;
    private GamePayload data;
}
