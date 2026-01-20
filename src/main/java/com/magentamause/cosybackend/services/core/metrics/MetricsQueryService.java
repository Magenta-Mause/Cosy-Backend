package com.magentamause.cosybackend.services.core.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsQueryService {
    private final InfluxDBClient influxDBClient;

    public List<MetricPointDto> queryMetrics(
            String gameServerUuid, MetricType metricType, Instant start, Instant end) {
        String flux = buildInfluxQuery(gameServerUuid, metricType, start, end);

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);

        List<MetricPointDto> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                results.add(
                        MetricPointDto.builder()
                                .time(record.getTime())
                                .value(
                                        record.getValue() != null
                                                ? ((Number) record.getValue()).doubleValue()
                                                : null)
                                .build());
            }
        }

        return results;
    }

    private String buildInfluxQuery(
            String gameServerUuid, MetricType metricType, Instant start, Instant end) {
        return String.format(
                "from(bucket: \"cosy-bucket\") "
                        + "|> range(start: %s, stop: %s) "
                        + "|> filter(fn: (r) => r[\"_measurement\"] == \"metrics\") "
                        + "|> filter(fn: (r) => r[\"container_name\"] == \"%s\") "
                        + "|> filter(fn: (r) => r[\"_field\"] == \"%s\") "
                        + "|> aggregateWindow(every: 10s, fn: mean, createEmpty: false) "
                        + "|> yield(name: \"mean\")",
                start.toString(), end.toString(), gameServerUuid, metricType.getValue());
    }
}
