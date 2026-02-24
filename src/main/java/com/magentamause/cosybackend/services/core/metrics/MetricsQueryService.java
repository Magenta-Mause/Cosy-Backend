package com.magentamause.cosybackend.services.core.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsQueryService {
    private static final String TYPE_SUFFIX_STRING = "__s";
    private static final String TYPE_SUFFIX_INT = "__i";
    private static final String TYPE_SUFFIX_FLOAT = "__f";
    private static final String TYPE_SUFFIX_BOOL = "__b";
    private static final Set<String> NON_CUSTOM_COLUMNS =
            Set.of(
                    "result",
                    "table",
                    "_start",
                    "_stop",
                    "_time",
                    "_measurement",
                    "game_server_uuid",
                    MetricType.CPU_PERCENT.getValue(),
                    MetricType.MEMORY_PERCENT.getValue(),
                    MetricType.MEMORY_USAGE.getValue(),
                    MetricType.MEMORY_LIMIT.getValue(),
                    MetricType.NETWORK_INPUT.getValue(),
                    MetricType.NETWORK_OUTPUT.getValue(),
                    MetricType.BLOCK_READ.getValue(),
                    MetricType.BLOCK_WRITE.getValue());
    private final InfluxDBClient influxDBClient;

    public List<MetricPointDto> queryMetrics(
            String gameServerUuid, Instant start, Instant end, int point) {
        String flux = buildInfluxQuery(gameServerUuid, start, end, point);

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);

        List<MetricPointDto> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Map<String, Object> customMetricHolder = extractCustomMetrics(record);

                MetricPointDto.MetricValues metrics =
                        MetricPointDto.MetricValues.ofFluxRecord(record, customMetricHolder);

                results.add(
                        MetricPointDto.builder()
                                .gameServerUuid(gameServerUuid)
                                .time(record.getTime())
                                .metricValues(metrics)
                                .build());
            }
        }

        if (results.isEmpty()) {
            log.debug("No metrics found for query {}, generating zero-value data points", flux);
            return generateZeroValueMetrics(gameServerUuid, start, end, point);
        }

        return results;
    }

    private Map<String, Object> extractCustomMetrics(FluxRecord record) {
        Map<String, Object> custom = new HashMap<>();
        Map<String, Object> values = record.getValues();
        if (values == null || values.isEmpty()) {
            return custom;
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || NON_CUSTOM_COLUMNS.contains(key)) {
                continue;
            }

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            String baseKey = stripTypeSuffix(key);
            custom.put(baseKey, value);
        }
        return custom;
    }

    private String stripTypeSuffix(String key) {
        if (key.endsWith(TYPE_SUFFIX_STRING)
                || key.endsWith(TYPE_SUFFIX_INT)
                || key.endsWith(TYPE_SUFFIX_FLOAT)
                || key.endsWith(TYPE_SUFFIX_BOOL)) {
            return key.substring(0, key.length() - 3);
        }
        return key;
    }

    private String buildInfluxQuery(
            String gameServerUuid, Instant start, Instant end, int pointCount) {
        long totalSeconds = end.getEpochSecond() - start.getEpochSecond();

        long intervalSeconds = Math.max(1, totalSeconds / pointCount);
        String time = intervalSeconds + "s";

        return String.format(
                "from(bucket: \"cosy-bucket\") "
                        + "|> range(start: %s, stop: %s) "
                        + "|> filter(fn: (r) => r[\"_measurement\"] == \"metrics\") "
                        + "|> filter(fn: (r) => r[\"game_server_uuid\"] == \"%s\") "
                        + "|> aggregateWindow(every: %s, fn: last, createEmpty: true) "
                        + "|> pivot( rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") ",
                start.toString(), end.toString(), gameServerUuid, time);
    }

    private List<MetricPointDto> generateZeroValueMetrics(
            String gameServerUuid, Instant start, Instant end, int pointCount) {
        List<MetricPointDto> zeroMetrics = new ArrayList<>();

        long totalSeconds = end.getEpochSecond() - start.getEpochSecond();
        long intervalSeconds = totalSeconds / pointCount;

        MetricPointDto.MetricValues zeroValues =
                MetricPointDto.MetricValues.builder()
                        .cpuPercent(0.0)
                        .memoryPercent(0.0)
                        .memoryUsage(0L)
                        .memoryLimit(0L)
                        .networkInput(0L)
                        .networkOutput(0L)
                        .blockRead(0L)
                        .blockWrite(0L)
                        .customMetricHolder(new HashMap<>())
                        .build();

        for (int i = 0; i < pointCount; i++) {
            Instant timestamp = start.plusSeconds(i * intervalSeconds);
            zeroMetrics.add(
                    MetricPointDto.builder()
                            .gameServerUuid(gameServerUuid)
                            .time(timestamp)
                            .metricValues(zeroValues)
                            .build());
        }

        return zeroMetrics;
    }
}
