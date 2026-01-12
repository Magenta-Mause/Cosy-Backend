package com.magentamause.cosybackend.services.external.loki;

import com.magentamause.cosybackend.configs.properties.LokiProperties;
import com.magentamause.cosybackend.dtos.loki.LokiLogQuery;
import com.magentamause.cosybackend.dtos.loki.LokiPushRequest;
import com.magentamause.cosybackend.dtos.loki.LokiQueryResponse;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(LokiProperties.class)
public class LokiQueryService {

    private final WebClient lokiWebClient;
    private final LokiProperties lokiProperties;

    public List<GameServerLogMessageEntity> queryLogs(LokiLogQuery query, TemporalAmount since) {
        String logQl = buildLogQl(query);
        Instant end = Instant.now();
        Instant start = end.minus(since);

        LokiQueryResponse response =
                lokiWebClient
                        .get()
                        .uri(
                                "/loki/api/v1/query_range?query={query}&limit={limit}&start={since}&end={end}",
                                logQl,
                                query.limit(),
                                toNs(start),
                                toNs(end))
                        .retrieve()
                        .bodyToMono(LokiQueryResponse.class)
                        .block();

        return LokiMapper.toEntities(response);
    }

    public void saveGameServerLog(GameServerLogMessageEntity logEntity) {

        long timestampNs = logEntity.getTimestamp().toEpochMilli() * 1_000_000;

        Map<String, String> labels = new java.util.HashMap<>();
        labels.put("app", lokiProperties.applicationName());
        labels.put("level", logEntity.getLevel().name());

        if (logEntity.getGameServerUuid() != null) {
            labels.put("server_uuid", logEntity.getGameServerUuid());
        }

        LokiPushRequest payload =
                new LokiPushRequest(
                        List.of(
                                new LokiPushRequest.LokiStream(
                                        labels,
                                        List.of(
                                                List.of(
                                                        String.valueOf(timestampNs),
                                                        logEntity.getMessage())))));

        lokiWebClient
                .post()
                .uri("/loki/api/v1/push")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void saveGameServerLogs(List<GameServerLogMessageEntity> logs) {
        Map<Map<String, String>, List<List<String>>> grouped = new java.util.HashMap<>();

        for (GameServerLogMessageEntity log : logs) {
            Map<String, String> labels =
                    Map.of(
                            "app", lokiProperties.applicationName(),
                            "level", log.getLevel().name(),
                            "server_uuid", log.getGameServerUuid());

            grouped.computeIfAbsent(labels, k -> new java.util.ArrayList<>())
                    .add(
                            List.of(
                                    String.valueOf(log.getTimestamp().toEpochMilli() * 1_000_000),
                                    log.getMessage()));
        }

        LokiPushRequest payload =
                new LokiPushRequest(
                        grouped.entrySet().stream()
                                .map(e -> new LokiPushRequest.LokiStream(e.getKey(), e.getValue()))
                                .toList());

        lokiWebClient
                .post()
                .uri("/loki/api/v1/push")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private String buildLogQl(LokiLogQuery q) {
        StringBuilder sb = new StringBuilder("{app=\"" + lokiProperties.applicationName() + "\"");

        if (q.gameServerUuid() != null) {
            sb.append(",server_uuid=\"").append(q.gameServerUuid()).append("\"");
        }
        sb.append("}");

        if (q.levels() != null && !q.levels().isEmpty()) {
            sb.append(" | json | level =~ \"")
                    .append(
                            q.levels().stream()
                                    .map(Enum::name)
                                    .reduce((a, b) -> a + "|" + b)
                                    .orElse(""))
                    .append("\"");
        }

        return sb.toString();
    }

    private static Long toNs(Instant i) {
        if (i == null) throw new RuntimeException("Cannot convert null Instant to nanoseconds");
        return i.toEpochMilli() * 1_000_000;
    }
}
