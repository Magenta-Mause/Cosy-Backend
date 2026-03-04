package com.magentamause.cosybackend.dtos.actiondtos.gameserver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.influxdb.query.FluxRecord;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        public static MetricValues ofFluxRecord(
                FluxRecord fluxRecord, Map<String, Object> customMetricHolder) {
            return MetricValues.builder()
                    .cpuPercent(
                            toDouble(fluxRecord.getValueByKey(MetricType.CPU_PERCENT.getValue())))
                    .memoryPercent(
                            toDouble(
                                    fluxRecord.getValueByKey(MetricType.MEMORY_PERCENT.getValue())))
                    .memoryUsage(
                            toLong(fluxRecord.getValueByKey(MetricType.MEMORY_USAGE.getValue())))
                    .memoryLimit(
                            toLong(fluxRecord.getValueByKey(MetricType.MEMORY_LIMIT.getValue())))
                    .networkInput(
                            toLong(fluxRecord.getValueByKey(MetricType.NETWORK_INPUT.getValue())))
                    .networkOutput(
                            toLong(fluxRecord.getValueByKey(MetricType.NETWORK_OUTPUT.getValue())))
                    .blockRead(toLong(fluxRecord.getValueByKey(MetricType.BLOCK_READ.getValue())))
                    .blockWrite(toLong(fluxRecord.getValueByKey(MetricType.BLOCK_WRITE.getValue())))
                    .customMetricHolder(customMetricHolder)
                    .build();
        }

        private static Double toDouble(Object value) {
            return value == null ? null : ((Number) value).doubleValue();
        }

        private static Long toLong(Object value) {
            return value == null ? null : ((Number) value).longValue();
        }

        public MetricValues setCustomMetricHolder(Map<String, Object> customMetricHolder) {
            this.customMetricHolder = customMetricHolder;
            return this;
        }

        public Map<String, Number> coreMetricsToMap() {
            HashMap<String, Number> coreMetrics = new HashMap<>();
            putIfNotNull(coreMetrics, MetricType.CPU_PERCENT.getValue(), this.cpuPercent);
            putIfNotNull(coreMetrics, MetricType.MEMORY_PERCENT.getValue(), this.memoryPercent);
            putIfNotNull(coreMetrics, MetricType.MEMORY_USAGE.getValue(), this.memoryUsage);
            putIfNotNull(coreMetrics, MetricType.MEMORY_LIMIT.getValue(), this.memoryLimit);
            putIfNotNull(coreMetrics, MetricType.NETWORK_INPUT.getValue(), this.networkInput);
            putIfNotNull(coreMetrics, MetricType.NETWORK_OUTPUT.getValue(), this.networkOutput);
            putIfNotNull(coreMetrics, MetricType.BLOCK_READ.getValue(), this.blockRead);
            putIfNotNull(coreMetrics, MetricType.BLOCK_WRITE.getValue(), this.blockWrite);

            return coreMetrics;
        }

        public static MetricValues fromCoreMetrics(Map<String, Number> coreMetrics) {
            return MetricValues.builder()
                    .cpuPercent((Double) coreMetrics.get(MetricType.CPU_PERCENT.getValue()))
                    .memoryPercent((Double) coreMetrics.get(MetricType.MEMORY_PERCENT.getValue()))
                    .memoryUsage((Long) coreMetrics.get(MetricType.MEMORY_USAGE.getValue()))
                    .memoryLimit((Long) coreMetrics.get(MetricType.MEMORY_LIMIT.getValue()))
                    .networkInput((Long) coreMetrics.get(MetricType.NETWORK_INPUT.getValue()))
                    .networkOutput((Long) coreMetrics.get(MetricType.NETWORK_OUTPUT.getValue()))
                    .blockRead((Long) coreMetrics.get(MetricType.BLOCK_READ.getValue()))
                    .blockWrite((Long) coreMetrics.get(MetricType.BLOCK_WRITE.getValue()))
                    .build();
        }

        private void putIfNotNull(HashMap<String, Number> map, String key, Number value) {
            if (value != null) {
                map.put(key, value);
            }
        }
    }
}
