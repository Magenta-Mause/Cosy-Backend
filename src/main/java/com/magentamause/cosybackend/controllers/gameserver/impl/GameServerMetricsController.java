package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerMetricsApi;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import com.magentamause.cosybackend.services.core.metrics.MetricsService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameServerMetricsController implements GameServerMetricsApi {
    private final MetricsService metricsService;
    private final GameServerService gameServerService;

    @Override
    @NeedsValidation(value = Operation.GAME_SERVER_METRIC_READ)
    public ResponseEntity<List<MetricPointDto>> getMetrics(
            @ResourceId String gameServerUuid,
            Instant end,
            Instant start,
            int pointCount) {
        TimeRange range = resolveAndValidateTimeRange(start, end);

        return ResponseEntity.ok(
                metricsService.queryMetrics(
                        gameServerUuid, range.start(), range.end(), pointCount));
    }

    @Override
    @NeedsValidation(value = Operation.GAME_SERVER_METRIC_READ_PUBLIC, allowUnauthorized = true)
    public ResponseEntity<List<MetricPointDto>> getPublicEvaluableMetrics(
            @ResourceId String gameServerUuid,
            Instant end,
            Instant start,
            int pointCount) {
        TimeRange range = resolveAndValidateTimeRange(start, end);

        if (!gameServerService.isGameServerPubliclyEvaluable(gameServerUuid)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Game server is not publicly evaluable");
        }

        return ResponseEntity.ok(
                metricsService.queryPublicMetrics(
                        gameServerUuid, range.start(), range.end(), pointCount));
    }

    private TimeRange resolveAndValidateTimeRange(Instant start, Instant end) {
        Instant now = Instant.now();
        Instant resolvedEnd = end != null ? end : now;
        Instant resolvedStart = start != null ? start : now.minus(Duration.ofHours(1));

        if (resolvedEnd.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "end must not be in the future");
        }

        if (!resolvedStart.isBefore(resolvedEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be before end");
        }

        return new TimeRange(resolvedStart, resolvedEnd);
    }

    private record TimeRange(Instant start, Instant end) {}
}
