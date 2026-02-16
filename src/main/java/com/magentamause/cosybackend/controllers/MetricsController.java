package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.metrics.MetricsQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private final MetricsQueryService queryService;

    @GetMapping("/{gameServerUuid}")
    @NeedsValidation(Operation.GAME_SERVER_METRIC_READ)
    public ResponseEntity<List<MetricPointDto>> getMetrics(
            @ResourceId @PathVariable String gameServerUuid,
            @RequestParam(required = false) Instant end,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false, defaultValue = "100") int pointCount) {
        Instant now = Instant.now();
        Instant defaultEnd = (end != null) ? end : now;
        Instant defaultStart = (start != null) ? start : now.minus(Duration.ofHours(1));

        if (defaultEnd.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "end must not be in the future");
        }

        if (defaultStart.isAfter(defaultEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be before end");
        }

        if (defaultEnd.equals(defaultStart)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "start and end must not be equal");
        }

        return ResponseEntity.ok(
                queryService.queryMetrics(gameServerUuid, defaultStart, defaultEnd, pointCount));
    }
}
