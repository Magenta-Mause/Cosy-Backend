package com.magentamause.cosybackend.services.core.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.magentamause.cosybackend.configs.properties.InfluxProperties;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.MetricPointDto;
import com.magentamause.cosybackend.entities.PublicDashboard;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.layout.DashboardTypes;
import com.magentamause.cosybackend.entities.layout.PublicDashboardLayout;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.entities.metric.MetricType;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.websockets.GameServerMetricsPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    private static final String TYPE_SUFFIX_STRING = "__s";
    private static final String TYPE_SUFFIX_INT = "__i";
    private static final String TYPE_SUFFIX_FLOAT = "__f";
    private static final String TYPE_SUFFIX_BOOL = "__b";

    private final InfluxDBClient influxDBClient;
    private final InfluxProperties influxProperties;
    private final EngineManager engineManager;
    private final GameServerRepository gameServerRepository;
    private final GameServerMetricsPublisher gameServerMetricsPublisher;

    private final MetricsQueryService metricsQueryService;
    private final GameServerService gameServerService;
    private final MetricsUtilService metricsUtilService;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    public void collectMetrics() {
        List<GameServerEntity> gameServers = gameServerRepository.findAll();
        try {
            for (GameServerEntity gameServer : gameServers) {
                try {
                    Optional<Metric> metric = engineManager.collectMetric(gameServer);
                    if (metric.isPresent()) {
                        Point point = convertMetricToPoint(metric.get());
                        writeToInfluxDB(point);
                        gameServerMetricsPublisher.publishMetrics(
                                gameServer.getUuid(), metric.get().toDto());
                    } else {
                        gameServerMetricsPublisher.publishMetrics(
                                gameServer.getUuid(),
                                Metric.builder()
                                        .cpuPercent(0.0)
                                        .memoryLimit(0L)
                                        .memoryUsage(0L)
                                        .blockRead(0L)
                                        .blockWrite(0L)
                                        .networkInput(0L)
                                        .networkOutput(0L)
                                        .gameServerUuid(gameServer.getUuid())
                                        .time(Instant.now())
                                        .build()
                                        .toDto());
                    }
                } catch (Exception e) {
                    log.error(
                            "Failed to collect metrics for container {}: {}",
                            gameServer,
                            e.getMessage(),
                            e);
                }
            }
        } catch (Exception e) {
            log.error("Error during metrics collection: {}", e.getMessage(), e);
        }
    }

    public List<MetricPointDto> queryMetrics(
            String gameServerUuid, Instant start, Instant end, int pointCount) {
        return metricsQueryService.queryMetrics(gameServerUuid, start, end, pointCount);
    }

    public List<MetricPointDto> queryPublicMetrics(
            String gameServerUuid, Instant start, Instant end, int pointCount) {
        List<MetricPointDto> metrics = queryMetrics(gameServerUuid, start, end, pointCount);
        List<String> publiclyAccessible =
                extractPublicMetrics(
                        gameServerService.getOrThrow(gameServerUuid).getPublicDashboard());
        return metricsUtilService.filterMetrics(metrics, publiclyAccessible.toArray(String[]::new));
    }

    public List<String> extractPublicMetrics(PublicDashboard dashboard) {
        return !dashboard.isPublicDashboardEnabled()
                ? List.of()
                : dashboard.getPublicDashboardLayouts().stream()
                        .filter(
                                layout ->
                                        layout.getPublicDashboardType()
                                                .equals(DashboardTypes.METRIC))
                        .map(PublicDashboardLayout::getMetricType)
                        .toList();
    }

    public void writeToInfluxDB(Point point) {
        try {
            influxDBClient
                    .getWriteApiBlocking()
                    .writePoint(influxProperties.bucket(), influxProperties.org(), point);
        } catch (Exception e) {
            log.error("Failed to write point for container {}: {}", point, e.getMessage(), e);
        }
    }

    public Point convertMetricToPoint(Metric metrics) {
        Point point =
                Point.measurement("metrics")
                        .addTag("game_server_uuid", metrics.getGameServerUuid())
                        .addField(MetricType.CPU_PERCENT.getValue(), metrics.getCpuPercent())
                        .addField(MetricType.MEMORY_USAGE.getValue(), metrics.getMemoryUsage())
                        .addField(MetricType.MEMORY_LIMIT.getValue(), metrics.getMemoryLimit())
                        .addField(MetricType.MEMORY_PERCENT.getValue(), metrics.getMemoryPercent())
                        .addField(MetricType.NETWORK_INPUT.getValue(), metrics.getNetworkInput())
                        .addField(MetricType.NETWORK_OUTPUT.getValue(), metrics.getNetworkOutput())
                        .addField(MetricType.BLOCK_READ.getValue(), metrics.getBlockRead())
                        .addField(MetricType.BLOCK_WRITE.getValue(), metrics.getBlockWrite())
                        .time(metrics.getTime(), WritePrecision.NS);

        addCustomFields(point, metrics.getCustomMetricHolder());
        return point;
    }

    private void addCustomFields(Point point, Map<String, Object> customMetricHolder) {
        if (customMetricHolder == null || customMetricHolder.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> e : customMetricHolder.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();

            if (key == null || key.isBlank() || value == null) {
                continue;
            }

            String fieldKey = toTypedFieldKey(key, value);
            Object normalizedValue = normalizeFieldValue(value);

            switch (normalizedValue) {
                case null -> {}
                case String s -> point.addField(fieldKey, s);
                case Boolean b -> point.addField(fieldKey, b);
                case Double d -> point.addField(fieldKey, d);
                case Float f -> point.addField(fieldKey, f.doubleValue());
                case Long l -> point.addField(fieldKey, l);
                case Integer i -> point.addField(fieldKey, i.longValue());
                case Number n -> point.addField(fieldKey, n.doubleValue());
                default -> point.addField(fieldKey, String.valueOf(normalizedValue));
            }
        }
    }

    private String toTypedFieldKey(String key, Object value) {
        if (value instanceof String) {
            return key + TYPE_SUFFIX_STRING;
        }
        if (value instanceof Boolean) {
            return key + TYPE_SUFFIX_BOOL;
        }
        if (value instanceof Float || value instanceof Double) {
            return key + TYPE_SUFFIX_FLOAT;
        }
        if (value instanceof Number) {
            return key + TYPE_SUFFIX_INT;
        }
        // fallback: store unknown types as strings
        return key + TYPE_SUFFIX_STRING;
    }

    private Object normalizeFieldValue(Object value) {
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Float || value instanceof Double) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return String.valueOf(value);
    }
}
