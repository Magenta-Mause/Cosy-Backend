package com.magentamause.cosybackend.services.metrics;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.magentamause.cosybackend.entities.Metrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final DockerClient dockerClient;

    public Metrics collectMetrics(String containerId) throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(1);

        InspectContainerResponse container =
                dockerClient.inspectContainerCmd(containerId).exec();

        Metrics.MetricsBuilder builder = Metrics.builder()
                .uuid(containerId)
                .name(container.getName().replace("/", ""));

        StatsCmd statsCmd = dockerClient.statsCmd(containerId);
        statsCmd.exec(new StatsCallback(builder, latch));

        latch.await(5, TimeUnit.SECONDS);

        return builder
                .time(Instant.now())
                .build();
    }
}
