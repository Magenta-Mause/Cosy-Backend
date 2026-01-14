package com.magentamause.cosybackend.services.metrics;

import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.magentamause.cosybackend.configs.InfluxConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetricsQueryService {
        private final InfluxConfig influxConfig;

        public List<Map<String, Object>> queryMetrics(String containerId, String metricType, String timeRange) {
            String fieldName = getFieldName(metricType);

            String flux = String.format(
                    "from(bucket: \"cosy-bucket\") " +
                            "|> range(start: -%s) " +
                            "|> filter(fn: (r) => r[\"_measurement\"] == \"metrics\") " +
                            "|> filter(fn: (r) => r[\"container_uuid\"] == \"%s\") " +
                            "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                            "|> aggregateWindow(every: 1m, fn: mean, createEmpty: false) " +
                            "|> yield(name: \"mean\")",
                    timeRange, containerId, fieldName
            );

            List<FluxTable> tables = influxConfig.getClient().getQueryApi().query(flux);
            List<Map<String, Object>> results = new ArrayList<>();

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Map<String, Object> point = new HashMap<>();
                    point.put("time", record.getTime());
                    point.put("value", record.getValue());
                    results.add(point);
                }
            }

            return results;
        }

    private String getFieldName(String metricType) {
        return switch (metricType.toUpperCase()) {
            case "CPU" -> "cpu_percent";
            case "RAM" -> "memory_usage";
            case "MEMORY_LIMIT" -> "memory_limit";
            case "MEMORY_PERCENT" -> "memory_percent";
            case "NETWORK_INPUT" -> "network_input";
            case "NETWORK_OUTPUT" -> "network_output";
            case "BLOCK_READ" -> "block_read";
            case "BLOCK_WRITE" -> "block_write";
            default -> throw new IllegalArgumentException("Invalid metric type: " + metricType +
                    "Valid types are: CPU, RAM, MEMORY_LIMIT, MEMORY_PERCENT, NETWORK_INPUT, NETWORK_OUTPUT, BLOCK_READ, BLOCK_WRITE");
        };
    }

    public void close() {
        influxConfig.close();
    }
}
