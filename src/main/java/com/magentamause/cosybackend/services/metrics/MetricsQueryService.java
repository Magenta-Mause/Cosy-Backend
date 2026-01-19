package com.magentamause.cosybackend.services.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
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
                                .time(record.getTime())
                                .metricValues(metrics)
                                .build());
            }
        }

        return results;
    }

    private String buildInfluxQuery(String gameServerUuid, Instant start, Instant end) {
        return String.format(
                "from(bucket: \"cosy-bucket\") "
                        + "|> range(start: %s, stop: %s) "
                        + "|> filter(fn: (r) => r[\"_measurement\"] == \"metrics\") "
                        + "|> filter(fn: (r) => r[\"game_server_uuid\"] == \"%s\") "
                        + "|> aggregateWindow(every: 10s, fn: mean, createEmpty: false) "
                        + "|> pivot( rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") ",
                start.toString(), end.toString(), gameServerUuid);
    }

    private Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
