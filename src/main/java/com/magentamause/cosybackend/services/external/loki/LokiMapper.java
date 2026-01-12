package com.magentamause.cosybackend.services.external.loki;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magentamause.cosybackend.dtos.loki.LokiQueryResponse;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

@Slf4j
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
                                                value ->
                                                        GameServerLogMessageEntity.builder()
                                                                .message(value.get(1))
                                                                .gameServerUuid(result.stream().get("server_uuid"))
                                                                .timestamp(
                                                                        Instant.ofEpochMilli(
                                                                                Long.parseLong(
                                                                                        value.get(
                                                                                                0)) / 1_000_000))
                                                                .level(result.stream().get("level").equals("ERROR") ? GameServerLogMessageEntity.LogLevel.ERROR :
                                                                        result.stream().get("level").equals("INFO") ? GameServerLogMessageEntity.LogLevel.INFO :
                                                                                GameServerLogMessageEntity.LogLevel.TRACE)
                                                                .build()
                                        ))
                .toList();
    }
}
