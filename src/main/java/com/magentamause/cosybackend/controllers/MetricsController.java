package com.magentamause.cosybackend.controllers;

import com.influxdb.client.write.Point;
import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import com.magentamause.cosybackend.entities.Metric;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.metrics.MetricsQueryService;
import com.magentamause.cosybackend.services.metrics.MetricsService;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private final MetricsQueryService queryService;

    @GetMapping("/{gameServerUuid}")
    public List<MetricPointDto> getMetrics(
            @PathVariable String gameServerUuid,
            @RequestParam String type,
            @RequestParam(defaultValue = "1h") String range) {
        return queryService.queryMetrics(gameServerUuid, type, range);
    }
}
