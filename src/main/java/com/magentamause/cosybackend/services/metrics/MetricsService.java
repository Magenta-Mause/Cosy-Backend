package com.magentamause.cosybackend.services.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.magentamause.cosybackend.configs.properties.InfluxProperties;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.entities.metric.MetricType;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.engine.EngineManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    private final InfluxDBClient influxDBClient;
    private final InfluxProperties influxProperties;
    private final EngineManager engineManager;
    private final GameServerRepository gameServerRepository;

    public Point convertMetricToPoint(Metric metrics) {
        return Point.measurement("metrics")
                .addTag("container_uuid", metrics.getUuid())
                .addTag("container_name", metrics.getName().substring(5))
                .addField(MetricType.CPU_PERCENT.getValue(), metrics.getCpuPercent())
                .addField(MetricType.MEMORY_USAGE.getValue(), metrics.getMemoryUsage())
                .addField(MetricType.MEMORY_LIMIT.getValue(), metrics.getMemoryLimit())
                .addField(MetricType.MEMORY_PERCENT.getValue(), metrics.getMemoryPercent())
                .addField(MetricType.NETWORK_INPUT.getValue(), metrics.getNetworkInput())
                .addField(MetricType.NETWORK_OUTPUT.getValue(), metrics.getNetworkOutput())
                .addField(MetricType.BLOCK_READ.getValue(), metrics.getBlockRead())
                .addField(MetricType.BLOCK_WRITE.getValue(), metrics.getBlockWrite())
                .time(metrics.getTime(), WritePrecision.NS);
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

    @Scheduled(fixedRateString = "1s")
    public void collectMetrics() {
        List<GameServerEntity> gameServers = gameServerRepository.findAll();
        try {
            for (GameServerEntity gameServer : gameServers) {
                try {
                    Optional<Metric> metric = engineManager.collectMetric(gameServer);
                    if (metric.isPresent()) {
                        Point point = convertMetricToPoint(metric.get());
                        writeToInfluxDB(point);
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
}
