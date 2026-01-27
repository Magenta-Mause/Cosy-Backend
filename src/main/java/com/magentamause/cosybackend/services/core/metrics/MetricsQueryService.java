package com.magentamause.cosybackend.services.core.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsQueryService {
    private final InfluxDBClient influxDBClient;

    public List<MetricPointDto> queryMetrics(String gameServerUuid, Instant start, Instant end) {
        String flux = buildInfluxQuery(gameServerUuid, start, end);

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);

        List<MetricPointDto> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                MetricPointDto.MetricValues metrics =
                        MetricPointDto.MetricValues.builder()
                                .cpuPercent(toDouble(record.getValueByKey("cpu_percent")))
                                .memoryPercent(toDouble(record.getValueByKey("memory_percent")))
                                .memoryUsage(toLong(record.getValueByKey("memory_usage")))
                                .memoryLimit(toLong(record.getValueByKey("memory_limit")))
                                .networkInput(toLong(record.getValueByKey("network_input")))
                                .networkOutput(toLong(record.getValueByKey("network_output")))
                                .blockRead(toLong(record.getValueByKey("block_read")))
                                .blockWrite(toLong(record.getValueByKey("block_write")))
                                .build();

                results.add(
                        MetricPointDto.builder()
                                .gameServerUuid(gameServerUuid.substring(5))
                                .time(record.getTime())
                                .metricValues(metrics)
                                .build());
            }
        }

        return results;
    }

    private String buildInfluxQuery(String gameServerUuid, Instant start, Instant end) {
        long diff = end.getEpochSecond() - start.getEpochSecond();
        Duration duration = Duration.ofSeconds(diff);
        String time;
        if (duration.toMinutes() <= 30) {
            time = "5s";
        } else if (duration.toHours() <= 1) {
            time = "45s";
        } else if (duration.toHours() <= 24) {
            time = "10m";
        } else if (duration.toDays() <= 30) {
            time = "1h";
        } else {
            time = "1d";
        }

        return String.format(
                "from(bucket: \"cosy-bucket\") "
                        + "|> range(start: %s, stop: %s) "
                        + "|> filter(fn: (r) => r[\"_measurement\"] == \"metrics\") "
                        + "|> filter(fn: (r) => r[\"game_server_uuid\"] == \"%s\") "
                        + "|> aggregateWindow(every: %s, fn: mean, createEmpty: false) "
                        + "|> pivot( rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") ",
                start.toString(), end.toString(), gameServerUuid, time);
    }

    private Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
