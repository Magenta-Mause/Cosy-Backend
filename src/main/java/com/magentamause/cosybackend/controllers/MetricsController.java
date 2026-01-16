package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import com.magentamause.cosybackend.entities.metric.MetricType;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.services.metrics.MetricsQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private final MetricsQueryService queryService;

    @GetMapping("/{gameServerUuid}")
    @RequireAccess(action = Action.READ, resource = Resource.USER)
    public ResponseEntity<List<MetricPointDto>> getMetrics(
            @PathVariable String gameServerUuid,
            @RequestParam MetricType type,
            @RequestParam(required = false) Instant end,
            @RequestParam(required = false) Instant start) {
        Instant now = Instant.now();
        Instant defaultEnd = (end != null) ? end : now;
        Instant defaultStart = (start != null) ? start : now.minus(Duration.ofHours(1));

        if (defaultEnd.isAfter(now)) {
            return ResponseEntity.badRequest().body(null);
        }
        if (defaultStart.isAfter(defaultEnd)) {
            return ResponseEntity.badRequest().body(null);
        }
        if (defaultEnd.equals(defaultStart)) {
            return ResponseEntity.badRequest().body(null);
        }

        return ResponseEntity.ok(
                queryService.queryMetrics(gameServerUuid, type, defaultStart, defaultEnd));
    }
}
