package com.magentamause.cosybackend.services.metrics;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.magentamause.cosybackend.configs.InfluxConfig;
import com.magentamause.cosybackend.entities.Metrics;
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

    public Metrics collectMetrics(String containerId) throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(1);

        InspectContainerResponse container = dockerClient.inspectContainerCmd(containerId).exec();

        Metrics.MetricsBuilder builder =
                Metrics.builder().uuid(containerId).name(container.getName().replace("/", ""));

        dockerClient.statsCmd(containerId).exec(new StatsCallback(builder, latch));

        boolean success = latch.await(3, TimeUnit.SECONDS);
        if (!success) {
            log.warn("Stats collection timed out for {}", containerId);
        }

        return builder.time(Instant.now()).build();
    }

    public void writeMetrics(Metrics metrics) {
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

        influxConfig
                .getClient()
                .getWriteApiBlocking()
                .writePoint(influxConfig.getBucket(), influxConfig.getOrg(), point);
    }
}
