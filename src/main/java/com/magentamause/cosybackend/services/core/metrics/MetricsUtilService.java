package com.magentamause.cosybackend.services.core.metrics;

import com.influxdb.query.FluxRecord;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetricsUtilService {

    public List<MetricPointDto> filterMetrics(
            List<MetricPointDto> metrics, String[] visibleAttributes) {
        return metrics.stream()
                .map(metricPointDto -> filterMetricsValues(metricPointDto, visibleAttributes))
                .toList();
    }

    private MetricPointDto filterMetricsValues(
            MetricPointDto metricPointDto, String[] visibleAttributes) {
        Map<String, Number> coreMetricsMap = metricPointDto.getMetricValues().coreMetricsToMap();
        Map<String, Number> filteredCoreMetrics = new HashMap<>();
        Map<String, Object> customMetrics =
                metricPointDto.getMetricValues().getCustomMetricHolder();
        Map<String, Object> filteredCustomMetrics = new HashMap<>();
        for (String attribute : visibleAttributes) {
            if (coreMetricsMap.containsKey(attribute)) {
                filteredCoreMetrics.put(attribute, coreMetricsMap.get(attribute));
            }
            if (customMetrics.containsKey(attribute)) {
                filteredCustomMetrics.put(attribute, customMetrics.get(attribute));
            }
        }

        return MetricPointDto.builder()
                .time(metricPointDto.getTime())
                .gameServerUuid(metricPointDto.getGameServerUuid())
                .metricValues(
                        MetricPointDto.MetricValues.fromCoreMetrics(filteredCoreMetrics)
                                .setCustomMetricHolder(filteredCustomMetrics))
                .build();
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
}
