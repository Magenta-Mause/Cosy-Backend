package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import com.magentamause.cosybackend.entities.metric.MetricType;
import com.magentamause.cosybackend.services.metrics.MetricsQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private final MetricsQueryService queryService;

    @GetMapping("/{gameServerUuid}")
    public List<MetricPointDto> getMetrics(
            @PathVariable String gameServerUuid,
            @RequestParam MetricType type,
            @RequestParam(defaultValue = "1h") String range) {

        if (!range.matches("\\d+[mhd]")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid range format. Only minutes(m), hours(h), and days(d) are allowed."
            );
        }

        return queryService.queryMetrics(gameServerUuid, type, range);
    }
}
