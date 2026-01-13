package com.magentamause.cosybackend.services.metrics;

import com.influxdb.client.write.Point;
import com.influxdb.client.domain.WritePrecision;
import com.magentamause.cosybackend.configs.InfluxConfig;
import com.magentamause.cosybackend.entities.Metrics;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricsWriter {
    private InfluxConfig influxConfig;

    public void writeMetrics(Metrics metrics) {
        Point point = Point.measurement("docker_stats")
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

        influxConfig.getClient().getWriteApiBlocking().writePoint(point);
    }
}