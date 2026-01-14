package com.magentamause.cosybackend.controllers;

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
    private final MetricsService metricsService;
    private final EngineManager engineManager;

    @GetMapping("/{containerId}")
    public List<MetricPointDto> getMetrics(
            @PathVariable String containerId,
            @RequestParam String type,
            @RequestParam(defaultValue = "1h") String range) {
        return queryService.queryMetrics(containerId, type, range);
    }

    @Scheduled(fixedRateString = "1s")
    public void collectMetrics() {
        try {
            List<String> containerUuids = engineManager.getActiveContainerUuids();

            log.debug("Collecting metrics for {} containers", containerUuids.size());

            for (String containerUuid : containerUuids) {
                try {
                    Metric metric = metricsService.collectMetrics(containerUuid);
                    metricsService.mapMetricToPoint(metric);
                } catch (Exception e) {
                    log.error(
                            "Failed to collect metrics for container {}: {}",
                            containerUuid,
                            e.getMessage(),
                            e);
                }
            }

        } catch (Exception e) {
            log.error("Error during metrics collection: {}", e.getMessage(), e);
        }
    }
}
