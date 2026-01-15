package com.magentamause.cosybackend.services.metrics;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.magentamause.cosybackend.configs.properties.InfluxProperties;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.Metric;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.engine.EngineManager;
import java.util.List;
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
                .addTag("container_name", metrics.getName())
                .addField("cpu_percent", metrics.getCpuPercent())
                .addField("memory_usage", metrics.getMemoryUsage())
                .addField("memory_limit", metrics.getMemoryLimit())
                .addField("memory_percent", metrics.getMemoryPercent())
                .addField("network_input", metrics.getNetworkInput())
                .addField("network_output", metrics.getNetworkOutput())
                .addField("block_read", metrics.getBlockRead())
                .addField("block_write", metrics.getBlockWrite())
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
                    Metric metric = engineManager.collectMetric(gameServer);
                    if (metric != null) {
                        Point point = convertMetricToPoint(metric);
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
