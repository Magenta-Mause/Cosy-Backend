package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.services.engine.docker.util.StatsMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Service for collecting metrics from Docker containers. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerMetricsCollector {

    private final DockerClient client;
    private final StatsMapper statsMapper;
    private final DockerContainerFinder containerFinder;

    public Optional<Metric> collectMetric(GameServerEntity gameServer) throws InterruptedException {
        Optional<Container> containerOpt = containerFinder.findContainer(gameServer);
        if (containerOpt.isEmpty()) {
            return Optional.empty();
        }

        String containerUuid = containerOpt.get().getId();

        InspectContainerResponse container = client.inspectContainerCmd(containerUuid).exec();

        if (Boolean.FALSE.equals(container.getState().getRunning())) {
            statsMapper.clearState(containerUuid);
            return Optional.empty();
        }

        AtomicReference<Metric> statsRef = new AtomicReference<>();

        client.statsCmd(containerUuid)
                .exec(
                        new ResultCallback.Adapter<Statistics>() {
                            @Override
                            public void onNext(Statistics statistics) {
                                Metric stats = statsMapper.mapStats(containerUuid, statistics);
                                stats.setGameServerUuid(
                                        container.getName().replace("/", "").substring(5));
                                stats.setTime(Instant.now());
                                statsRef.set(stats);
                                try {
                                    close();
                                } catch (Exception e) {
                                    log.warn(
                                            "Failed to close Docker stats callback for container {}",
                                            containerUuid,
                                            e);
                                }
                            }
                        })
                .awaitCompletion();

        if (statsRef.get() != null) {
            statsRef.get().setCustomMetricHolder(gameServer.getCustomMetricHolder());
        }

        return Optional.ofNullable(statsRef.get());
    }
}
