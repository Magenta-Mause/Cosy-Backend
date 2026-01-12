package com.magentamause.cosybackend.services.external.loki;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magentamause.cosybackend.dtos.loki.LokiQueryResponse;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;

import java.time.Instant;
import java.util.List;

public class LokiMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<GameServerLogMessageEntity> toEntities(LokiQueryResponse response) {

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().result().stream()
                .flatMap(
                        result ->
                                result.values().stream()
                                        .map(
                                                value -> {

                                                    return GameServerLogMessageEntity.builder()
                                                            .message(value.get(1))
                                                            .gameServerUuid(result.stream().get("server_uuid"))
                                                            .timestamp(
                                                                    Instant.ofEpochSecond(
                                                                            Long.parseLong(
                                                                                    value.get(
                                                                                            0)) / 1_000_000))
                                                            .level(
                                                                    GameServerLogMessageEntity
                                                                            .LogLevel.INFO)
                                                            .build();
                                                }))
                .toList();
    }
}
