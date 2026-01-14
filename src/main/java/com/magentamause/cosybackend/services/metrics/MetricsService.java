package com.magentamause.cosybackend.services.metrics;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.magentamause.cosybackend.configs.InfluxConfig;
import com.magentamause.cosybackend.configs.properties.InfluxProperties;
import com.magentamause.cosybackend.entities.Metric;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    private final InfluxConfig influxConfig;
    private final DockerClient dockerClient;
    private final InfluxProperties influxProperties;

    public Metric collectMetrics(String containerId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        InspectContainerResponse container = dockerClient.inspectContainerCmd(containerId).exec();

        Metric.MetricBuilder builder =
                Metric.builder().uuid(containerId).name(container.getName().replace("/", ""));

        dockerClient.statsCmd(containerId).exec(new StatsCallback(builder, latch));

        int TIMEOUT_DURATION = 3;
        boolean success = latch.await(TIMEOUT_DURATION, TimeUnit.SECONDS);
        if (!success) {
            log.warn("Stats collection timed out for {}", containerId);
        }

        return builder.time(Instant.now()).build();
    }

    public void mapMetricToPoint(Metric metrics) {
        Point point =
                Point.measurement("metrics")
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

        try {
            influxConfig.influxDBClient()
                    .getWriteApiBlocking().writePoint(influxProperties.bucket(), influxProperties.org(), point);
        } catch (Exception e) {
            log.error("Failed to write metric for container {}: {}", metrics.getName(), e.getMessage(), e);
        }
    }
}
