package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.entities.Metrics;
import com.magentamause.cosybackend.services.metrics.MetricsQueryService;
import com.magentamause.cosybackend.services.metrics.MetricsService;
import com.magentamause.cosybackend.services.metrics.MetricsWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private MetricsQueryService queryService;
    private MetricsService metricsService;
    private MetricsWriter writer;

    @GetMapping("/{containerId}")
    public List<Map<String, Object>> getMetrics(
            @PathVariable String containerId,
            @RequestParam String type,
            @RequestParam(defaultValue = "1h") String range) {
        return queryService.queryMetrics(containerId, type, range);
    }

    @Scheduled(fixedRate = 30000)
    public void collectMetrics() {
        try {
            // Replace with your actual container IDs
            List<String> containerIds = Arrays.asList("container1", "container2");

            for (String containerId : containerIds) {
                Metrics metrics = metricsService.collectMetrics(containerId);
                writer.writeMetrics(metrics);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
