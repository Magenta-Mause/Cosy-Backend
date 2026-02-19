package com.magentamause.cosybackend.dtos.actiondtos.gameserver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MetricPointDto {

    private String gameServerUuid;
    private Instant time;
    private MetricValues metricValues;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MetricValues {
        private Double cpuPercent;
        private Double memoryPercent;
        private Long memoryUsage;
        private Long memoryLimit;
        private Long networkInput;
        private Long networkOutput;
        private Long blockRead;
        private Long blockWrite;

        private Map<String, Object> customMetricHolder;
    }
}
