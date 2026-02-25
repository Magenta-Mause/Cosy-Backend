package com.magentamause.cosybackend.services.core.metrics;

import com.influxdb.query.FluxRecord;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MetricsUtilService {

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

    public Map<String, Object> extractCustomMetrics(FluxRecord record) {
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
}
