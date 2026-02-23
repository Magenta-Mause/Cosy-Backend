package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.docker.util.DockerConfigurationMapper;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import com.magentamause.cosybackend.services.engine.docker.util.DockerHostConfigFactory;
import com.magentamause.cosybackend.services.engine.docker.util.DockerImageNameBuilder;
import com.magentamause.cosybackend.services.engine.docker.util.DockerStatsMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Main orchestrator for Docker container management. Delegates specific responsibilities to
 * specialized components.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerEngineManager implements EngineManager, Closeable {

    private final DockerClient client;
    private final DockerEventHandler eventHandler;
    private final DockerImageManager imageManager;
    private final DockerContainerFinder containerFinder;
    private final DockerMetricsCollector metricsCollector;
    private final DockerLogStreamer logStreamer;
    private final DockerCommandSender commandSender;
    private final DockerHostConfigFactory hostConfigFactory;
    private final DockerContainerNameResolver containerNameResolver;

    @Value("${cosy.custom-metrics.base-url}")
    private String COSY_METRICS_BASE_URL;

    @Value("${cosy.custom-metrics.period-seconds:1}")
    private int COSY_METRICS_PERIOD_SECONDS;

    @PostConstruct
    public void init() {
        // Event handler initializes itself via @PostConstruct
    }

    @Override
    public void attachStatusSupplier(
            String gameServerUuid, Supplier<GameServerDto.GameServerStatus> statusSupplier) {
        eventHandler.attachStatusSupplier(gameServerUuid, statusSupplier);
    }

    @Override
    public GameServerDto.GameServerStatus getStatus(GameServerEntity serverConfig) {
        Optional<Container> container = containerFinder.findContainer(serverConfig);
        if (container.isPresent()) {
            String state = container.get().getState();
            return DockerStatsMapper.mapDockerStateToGameServerStatus(state);
        }
        return GameServerDto.GameServerStatus.STOPPED;
    }

    @Override
    public void attachStatusListener(BiConsumer<GameServerStatusUpdateEventType, String> listener) {
        eventHandler.attachStatusListener(listener);
    }

    @Override
    public void start(
            GameServerEntity serverConfig,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback,
            Supplier<GameServerDto.GameServerStatus> gameServerStatusSupplier)
            throws InternalServiceStartException, DockerPullImageException {
        log.info(
                "Starting Docker container for server: {} with config: {}",
                serverConfig.getServerName(),
                serverConfig);

        Optional<Container> container = containerFinder.findContainer(serverConfig);

        if (container.isPresent()) {
            handleExistingContainer(container.get(), serverConfig);
        }

        String image = DockerImageNameBuilder.buildImageName(serverConfig);
        String containerName = containerNameResolver.containerName(serverConfig);

        imageManager.ensureImagePresent(
                image,
                progressListener,
                statusUpdater,
                imagePullStartCallback,
                imagePullEndCallback);

        List<String> cmd =
                Optional.ofNullable(serverConfig.getDockerExecutionCommand()).orElse(List.of());
        List<String> env =
                new ArrayList<>(
                        DockerConfigurationMapper.mapEnvironment(
                                serverConfig.getEnvironmentVariables()));
        List<ExposedPort> exposedPorts =
                DockerConfigurationMapper.mapExposedPorts(serverConfig.getPortMappings());

        addUtilEnvVars(env, serverConfig);

        log.info("Starting container {} with env {}", containerName, env);

        CreateContainerResponse response =
                client.createContainerCmd(image)
                        .withName(containerName)
                        .withCmd(cmd)
                        .withEnv(env)
                        .withExposedPorts(exposedPorts)
                        .withHostConfig(hostConfigFactory.buildHostConfig(serverConfig))
                        .withTty(true)
                        .withStdinOpen(true)
                        .withAttachStdin(true)
                        .exec();

        eventHandler.attachStatusSupplier(serverConfig.getUuid(), gameServerStatusSupplier);

        try {
            client.startContainerCmd(response.getId()).exec();
        } catch (InternalServerErrorException e) {
            client.removeContainerCmd(response.getId()).withForce(true).exec();
            throw new InternalServiceStartException(e);
        }
    }

    private void addUtilEnvVars(List<String> env, GameServerEntity serverConfig) {
        env.add("COSY_GAME_SERVER_UUID=" + serverConfig.getUuid());
        env.add("COSY_GAME_SERVER_NAME=" + serverConfig.getServerName());
        env.add("COSY_GAME_SERVER_OWNER=" + serverConfig.getOwner().getUsername());
        env.add("COSY_CONTAINER_SECRET=" + serverConfig.getContainerSecret());
        env.add("COSY_BASE_URL=" + COSY_METRICS_BASE_URL);
        env.add("COSY_METRICS_PERIOD_SECONDS=" + COSY_METRICS_PERIOD_SECONDS);
    }

    private void handleExistingContainer(Container container, GameServerEntity serverConfig)
            throws InternalServiceStartException {
        if (container.getState().equals("running")) {
            log.warn(
                    "Trying to start container which is already running: {} - {}",
                    serverConfig.getServerName(),
                    serverConfig.getUuid());
            return;
        }
        try {
            remove(serverConfig);
        } catch (Exception e) {
            log.error(
                    "Failed to remove existing non-running container for server {}. "
                            + "Aborting start to avoid inconsistent container state.",
                    serverConfig.getServerName(),
                    e);
            throw new InternalServiceStartException(e);
        }
    }

    @Override
    public void stopAndRemove(GameServerEntity serverConfig) {
        Container container =
                containerFinder
                        .findContainer(serverConfig)
                        .orElseThrow(
                                () ->
                                        new ServerAlreadyStoppedException(
                                                serverConfig.getServerName()));

        if (!"running".equalsIgnoreCase(container.getState())) {
            throw new ServerAlreadyStoppedException(serverConfig.getServerName());
        }

        logStreamer.detachLogListener(serverConfig.getUuid());
        client.stopContainerCmd(container.getId()).exec();
        remove(serverConfig);
    }

    public void remove(GameServerEntity serverConfig) {
        logStreamer.detachLogListener(serverConfig.getUuid());
        remove(serverConfig.getUuid());
    }

    public void remove(String uuid) {
        containerFinder
                .findContainer(uuid)
                .ifPresent(
                        container -> {
                            client.removeContainerCmd(container.getId()).withForce(true).exec();
                        });
    }

    @Override
    public void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener) {
        logStreamer.attachLogListener(serviceConfig, listener);
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        eventHandler.close();
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                throw new IOException("Failed to close DockerClient", e);
            }
        }
    }

    @Override
    public Optional<Metric> collectMetric(GameServerEntity gameServer) throws InterruptedException {
        return metricsCollector.collectMetric(gameServer);
    }

    @Override
    public void sendCommand(GameServerEntity serverConfig, String command) throws IOException {
        commandSender.sendCommand(serverConfig, command);
    }
}
