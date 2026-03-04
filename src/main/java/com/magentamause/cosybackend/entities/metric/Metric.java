package com.magentamause.cosybackend.entities.metric;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Metric {
    private String gameServerUuid;

    private Double cpuPercent;

    private Long memoryUsage;

    private Long memoryLimit;

    private Double memoryPercent;

    private Long networkInput;

    private Long networkOutput;

    private Long blockRead;

    private Long blockWrite;

    private Instant time;

    @Builder.Default private Map<String, Object> customMetricHolder = new HashMap<>();

    public MetricPointDto toDto() {
        MetricPointDto.MetricValues metricValues =
                MetricPointDto.MetricValues.builder()
                        .cpuPercent(this.getCpuPercent())
                        .memoryPercent(this.getMemoryPercent())
                        .memoryUsage(this.getMemoryUsage())
                        .memoryLimit(this.getMemoryLimit())
                        .networkInput(this.getNetworkInput())
                        .networkOutput(this.getNetworkOutput())
                        .blockRead(this.getBlockRead())
                        .blockWrite(this.getBlockWrite())
                        .customMetricHolder(this.getCustomMetricHolder())
                        .build();

        return MetricPointDto.builder()
                .gameServerUuid(this.getGameServerUuid())
                .time(this.getTime())
                .metricValues(metricValues)
                .build();
    }
}
