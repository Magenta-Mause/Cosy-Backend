package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.PullProgressDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.metric.Metric;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.util.DockerMappingUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.magentamause.cosybackend.services.engine.config.EngineProperties.Docker;
import com.magentamause.cosybackend.services.engine.docker.util.StatsMapper;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DockerEngineManager implements EngineManager, Closeable {

    private final DockerClient client;
    private final StatsMapper statsMapper;
    private final DockerMappingUtils dockerMappingUtils;

    private final Map<String, StatusListenerContext> statusListeners = new ConcurrentHashMap<>();
    private ResultCallback<Event> eventCallback;

    private record StatusListenerContext(
            Supplier<GameServerDto.GameServerStatus> currentStatusSupplier,
            Consumer<GameServerDto.GameServerStatus> listener) {}

    @PostConstruct
    public void init() {
        startEventListener();
    }

    public static ExposedPort portMappingToExposedPort(PortMapping pm) {
        return switch (pm.getProtocol()) {
            case TCP -> ExposedPort.tcp(pm.getContainerPort());
            case UDP -> ExposedPort.udp(pm.getContainerPort());
            default -> throw new IllegalArgumentException("Unknown port type: " + pm.getProtocol());
        };
    }

    @Override
    public void attachStatusListener(
            GameServerEntity serviceConfig,
            Supplier<GameServerDto.GameServerStatus> currentStatusSupplier,
            Consumer<GameServerDto.GameServerStatus> listener) {

        statusListeners.put(
                serviceConfig.getUuid(),
                new StatusListenerContext(currentStatusSupplier, listener));

        // Initial sync
        Optional<Container> container = findContainer(serviceConfig);
        GameServerDto.GameServerStatus newStatus;
        if (container.isPresent()) {
            String state = container.get().getState();
            newStatus = dockerMappingUtils.mapDockerStateToGameServerStatus(state);
        } else {
            newStatus = GameServerDto.GameServerStatus.STOPPED;
        }

        // Do not overwrite PULLING_IMAGE with STOPPED if container is missing (it's expected)
        if (currentStatusSupplier.get() == GameServerDto.GameServerStatus.PULLING_IMAGE
                && newStatus == GameServerDto.GameServerStatus.STOPPED) {
            return;
        }

        if (currentStatusSupplier.get() != newStatus) {
            listener.accept(newStatus);
        }
    }

    private void startEventListener() {
        eventCallback =
                new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Event event) {
                        handleEvent(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Error in Docker event listener", throwable);
                    }
                };

        client.eventsCmd()
                .withEventTypeFilter("container")
                .withEventFilter("create", "start", "die", "stop", "destroy", "pause", "unpause")
                .exec(eventCallback);
    }

    private void handleEvent(Event event) {
        try {
            if (event.getActor() == null || event.getActor().getAttributes() == null) {
                return;
            }

            String containerName = event.getActor().getAttributes().get("name");
            if (containerName == null || !containerName.startsWith("cosy-")) {
                return;
            }

            String uuid = containerName.substring(5); // Remove "cosy-" prefix
            StatusListenerContext context = statusListeners.get(uuid);

            if (context != null) {
                String eventName = event.getAction();

                GameServerDto.GameServerStatus newStatus;

                if ("die".equals(eventName)) {
                    String exitCodeStr = event.getActor().getAttributes().get("exitCode");
                    int exitCode = 0;
                    try {
                        if (exitCodeStr != null) {
                            exitCode = Integer.parseInt(exitCodeStr);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse exitCode from Docker event: {}", exitCodeStr);
                    }

                    GameServerDto.GameServerStatus currentStatus =
                            context.currentStatusSupplier.get();

                    if (currentStatus == GameServerDto.GameServerStatus.STOPPED) {
                        newStatus = GameServerDto.GameServerStatus.STOPPED;
                    } else {
                        newStatus =
                                exitCode != 0
                                        ? GameServerDto.GameServerStatus.FAILED
                                        : GameServerDto.GameServerStatus.STOPPED;
                    }
                } else {
                    newStatus = dockerMappingUtils.mapEventToStatus(eventName);
                }

                if (newStatus != null && context.currentStatusSupplier.get() != newStatus) {
                    log.debug(
                            "Event: {} -> Updated status for server {} to {}",
                            eventName,
                            uuid,
                            newStatus);
                    context.listener.accept(newStatus);
                }
            }
        } catch (Exception e) {
            log.error("Error handling Docker event: {}", event, e);
        }
    }

    @Override
    public List<Integer> start(
            GameServerEntity serverConfig,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater) {
        log.info("Starting Docker container for server {}", serverConfig.getServerName());
        Optional<Container> container = findContainer(serverConfig);

        if (container.isPresent()) {
            if (!container.get().getState().equals("running")) {
                client.startContainerCmd(container.get().getId()).exec();
            }
            return getInstancePorts(serverConfig);
        }

        String image = buildImageName(serverConfig);
        String containerName = containerName(serverConfig);

        ensureImagePresent(serverConfig, image, progressListener, statusUpdater);

        List<String> cmd = serverConfig.getDockerExecutionCommand();
        if (cmd == null) {
            cmd = List.of();
        }

        List<String> env = mapEnvironment(serverConfig.getEnvironmentVariables());
        if (env == null) {
            env = List.of();
        }

        List<ExposedPort> exposedPorts = mapExposedPorts(serverConfig.getPortMappings());
        if (exposedPorts == null) {
            exposedPorts = List.of();
        }

        CreateContainerResponse response =
                client.createContainerCmd(image)
                        .withName(containerName)
                        .withCmd(cmd)
                        .withEnv(env)
                        .withExposedPorts(exposedPorts)
                        .withHostConfig(buildHostConfig(serverConfig))
                        .exec();

        client.startContainerCmd(response.getId()).exec();
        return getInstancePorts(serverConfig);
    }

    @Override
    public void stop(GameServerEntity serverConfig) {
        Container container =
                findContainer(serverConfig)
                        .orElseThrow(
                                () ->
                                        new ServerAlreadyStoppedException(
                                                serverConfig.getServerName()));

        if (!"running".equalsIgnoreCase(container.getState())) {
            throw new ServerAlreadyStoppedException(serverConfig.getServerName());
        }

        client.stopContainerCmd(container.getId()).exec();
    }

    @Override
    public void attachLogListener(
            GameServerEntity serviceConfig, Consumer<GameServerLogMessageEntity> listener) {
        String containerName = containerName(serviceConfig);

        ResultCallback.Adapter<Frame> callback =
                new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Frame frame) {
                        String message = new String(frame.getPayload(), StandardCharsets.UTF_8);

                        GameServerLogMessageEntity logMessage =
                                GameServerLogMessageEntity.builder()
                                        .message(message)
                                        .level(
                                                frame.getStreamType() == StreamType.STDERR
                                                        ? GameServerLogMessageEntity.LogLevel.ERROR
                                                        : GameServerLogMessageEntity.LogLevel.INFO)
                                        .timestamp(Instant.now())
                                        .build();

                        listener.accept(logMessage);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        listener.accept(
                                GameServerLogMessageEntity.builder()
                                        .message(throwable.getMessage())
                                        .level(GameServerLogMessageEntity.LogLevel.ERROR)
                                        .timestamp(Instant.now())
                                        .build());
                    }

                    @Override
                    public void onComplete() {
                        log.debug("Log listener for container {} completed", containerName);
                    }

                    @Override
                    public void close() throws IOException {
                        super.close();
                        log.debug("Closing log listener for container {}", containerName);
                    }
                };

        client.logContainerCmd(containerName)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(0)
                .exec(callback);
    }

    private Optional<Container> findContainer(GameServerEntity serverConfig) {
        String nameToMatch = String.format("/%s", containerName(serverConfig));

        return client.listContainersCmd().withShowAll(true).exec().stream()
                .filter(
                        c ->
                                Arrays.asList(
                                                Optional.ofNullable(c.getNames())
                                                        .orElse(new String[0]))
                                        .contains(nameToMatch))
                .findFirst();
    }

    private String buildImageName(GameServerEntity serverConfig) {
        String tag = serverConfig.getDockerImageTag();
        return (tag == null || tag.isBlank())
                ? serverConfig.getDockerImageName()
                : String.format("%s:%s", serverConfig.getDockerImageName(), tag);
    }

    private String containerName(GameServerEntity serverConfig) {
        return String.format("cosy-%s", serverConfig.getUuid());
    }

    private List<String> mapEnvironment(List<EnvironmentVariableConfiguration> envs) {
        return Optional.ofNullable(envs).orElse(List.of()).stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<ExposedPort> mapExposedPorts(List<PortMapping> ports) {
        return Optional.ofNullable(ports).orElse(List.of()).stream()
                .map(DockerEngineManager::portMappingToExposedPort)
                .distinct()
                .collect(Collectors.toList());
    }

    private HostConfig buildHostConfig(GameServerEntity serverConfig) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        if (serverConfig.getPortMappings() != null && !serverConfig.getPortMappings().isEmpty()) {
            Ports portBindings = new Ports();
            serverConfig
                    .getPortMappings()
                    .forEach(
                            p -> {
                                ExposedPort exposed = portMappingToExposedPort(p);
                                portBindings.bind(
                                        exposed, Ports.Binding.bindPort(p.getInstancePort()));
                            });
            hostConfig.withPortBindings(portBindings);
        }

        if (serverConfig.getVolumeMounts() != null && !serverConfig.getVolumeMounts().isEmpty()) {
            List<Bind> binds =
                    serverConfig.getVolumeMounts().stream()
                            .map(
                                    v ->
                                            new Bind(
                                                    v.getHostPath(),
                                                    new Volume(v.getContainerPath()),
                                                    AccessMode.rw))
                            .toList();
            hostConfig.withBinds(binds);
        }

        return hostConfig;
    }

    private void ensureImagePresent(
            GameServerEntity serverConfig,
            String image,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater) {
        // TODO: refactor
        boolean exists =
                client.listImagesCmd().withImageNameFilter(image).exec().stream()
                        .anyMatch(
                                img -> {
                                    String[] tags = img.getRepoTags();
                                    return tags != null && Arrays.asList(tags).contains(image);
                                });

        if (!exists) {
            statusUpdater.accept(GameServerDto.GameServerStatus.PULLING_IMAGE);
            try {
                ResultCallback.Adapter<PullResponseItem> callback =
                        new ResultCallback.Adapter<>() {
                            @Override
                            public void onNext(PullResponseItem item) {
                                if (progressListener != null) {
                                    PullProgressDto.PullProgressDtoBuilder builder =
                                            PullProgressDto.builder()
                                                    .status(item.getStatus())
                                                    .id(item.getId());

                                    if (item.getProgressDetail() != null) {
                                        builder.current(item.getProgressDetail().getCurrent())
                                                .total(item.getProgressDetail().getTotal());
                                    }

                                    progressListener.accept(
                                            new StartEventDto.PullProgress(builder.build()));
                                }
                            }
                        };
                client.pullImageCmd(image).exec(callback).awaitCompletion();
            } catch (Exception e) {
                statusUpdater.accept(GameServerDto.GameServerStatus.FAILED);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException(
                        String.format("Failed to pull Docker image %s", image), e);
            }
        }
    }

    private List<Integer> getInstancePorts(GameServerEntity serverConfig) {
        return Optional.ofNullable(serverConfig.getPortMappings()).orElse(List.of()).stream()
                .map(PortMapping::getInstancePort)
                .collect(Collectors.toList());
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        if (eventCallback != null) {
            try {
                eventCallback.close();
            } catch (Exception e) {
                log.warn("Failed to close Docker event listener", e);
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                throw new IOException("Failed to close DockerClient", e);
            }
        }
    }

    @Override
    public Metric collectMetric(GameServerEntity gameServer) throws InterruptedException {
        Optional<Container> containerOpt = findContainer(gameServer);
        if (containerOpt.isEmpty()) {
            return null;
        }

        String containerUuid = containerOpt.get().getId();

        InspectContainerResponse container = client.inspectContainerCmd(containerUuid).exec();

        if (Boolean.FALSE.equals(container.getState().getRunning())) {
            return null;
        }

        Metric.MetricBuilder builder =
                Metric.builder().uuid(containerUuid).name(container.getName().replace("/", ""));

        client.statsCmd(containerUuid)
                .exec(
                        new ResultCallback.Adapter<Statistics>() {
                            @Override
                            public void onNext(Statistics statistics) {
                                statsMapper.mapStats(statistics, builder);
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

        return builder.time(Instant.now()).build();
    }
}
