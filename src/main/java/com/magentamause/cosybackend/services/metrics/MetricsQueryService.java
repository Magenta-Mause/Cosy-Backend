package com.magentamause.cosybackend.services.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.magentamause.cosybackend.configs.InfluxConfig;
import java.util.ArrayList;
import java.util.List;

import com.magentamause.cosybackend.dtos.actiondtos.MetricPointDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsQueryService {
    private final InfluxDBClient influxDBClient;


    public List<MetricPointDto> queryMetrics(
            String gameServerUuid, String metricType, String timeRange) {

        String flux = String.format(
                "from(bucket: \"cosy-bucket\") "
                        + "|> range(start: -%s) "
                        + "|> filter(fn: (r) => r[\"_measurement\"] == \"metrics\") "
                        + "|> filter(fn: (r) => r[\"container_uuid\"] == \"%s\") "
                        + "|> filter(fn: (r) => r[\"_field\"] == \"%s\") "
                        + "|> aggregateWindow(every: 10s, fn: mean, createEmpty: false) "
                        + "|> yield(name: \"mean\")",
                timeRange, gameServerUuid, metricType);

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);

        List<MetricPointDto> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                results.add(new MetricPointDto(
                        record.getTime(),
                        record.getValue() != null
                                ? ((Number) record.getValue()).doubleValue()
                                : null
                ));
            }
        }

        return results;
    }

    public void close() {
        influxDBClient.close();
    }
}
