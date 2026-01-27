package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.InternalServerErrorException;
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
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import com.magentamause.cosybackend.exceptions.docker.InternalServiceStartException;
import com.magentamause.cosybackend.services.core.gameserver.GameServerStatusUpdateEventType;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.docker.util.StatsMapper;
import com.magentamause.cosybackend.services.engine.util.DockerMappingUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerEngineManager implements EngineManager, Closeable {

    private final DockerClient client;
    private final StatsMapper statsMapper;
    private final DockerMappingUtils dockerMappingUtils;

    private final List<BiConsumer<GameServerStatusUpdateEventType, String>> statusListeners =
            new CopyOnWriteArrayList<>();
    private ResultCallback<Event> eventCallback;

    private record StatusListenerContext(
            Supplier<GameServerDto.GameServerStatus> currentStatusSupplier,
            Consumer<GameServerDto.GameServerStatus> listener) {}

    private final List<BiConsumer<GameServerStatusUpdateEventType, String>> failListeners =
            new CopyOnWriteArrayList<>();

    private final Map<String, Supplier<GameServerDto.GameServerStatus>> statusSuppliers =
            new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        startEventListener();
    }

    @Override
    public void attachStatusSupplier(
            String gameServerUuid, Supplier<GameServerDto.GameServerStatus> statusSupplier) {
        statusSuppliers.put(gameServerUuid, statusSupplier);
    }

    public static ExposedPort portMappingToExposedPort(PortMapping pm) {
        return switch (pm.getProtocol()) {
            case TCP -> ExposedPort.tcp(pm.getContainerPort());
            case UDP -> ExposedPort.udp(pm.getContainerPort());
            default -> throw new IllegalArgumentException("Unknown port type: " + pm.getProtocol());
        };
    }

    @Override
    public GameServerDto.GameServerStatus getStatus(GameServerEntity serverConfig) {
        Optional<Container> container = findContainer(serverConfig);
        if (container.isPresent()) {
            String state = container.get().getState();
            return dockerMappingUtils.mapDockerStateToGameServerStatus(state);
        }
        return GameServerDto.GameServerStatus.STOPPED;
    }

    @Override
    public void attachStatusListener(BiConsumer<GameServerStatusUpdateEventType, String> listener) {
        statusListeners.add(listener);
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
                .withEventFilter("start", "die", "stop", "pause", "unpause")
                .exec(eventCallback);
    }

    private void handleEvent(Event event) {
        if (event.getActor() == null || event.getActor().getAttributes() == null) {
            return;
        }

        String containerName = event.getActor().getAttributes().get("name");
        if (containerName == null || !containerName.startsWith("cosy-")) {
            return;
        }

        String uuid = containerName.substring(5); // Remove "cosy-" prefix
        String eventName = event.getAction();

        log.info("Handling Docker event: {} for server {}", eventName, uuid);

        if (eventName == null) {
            log.warn("Received Docker event with null action: {}", event);
            return;
        }

        switch (eventName) {
            case "start":
            case "unpause":
                statusListeners.forEach(
                        l -> l.accept(GameServerStatusUpdateEventType.STARTED, uuid));
                break;
            case "die":
                if (!statusSuppliers.containsKey(uuid)) {
                    log.warn("No status supplier for server with uuid: {} found", uuid);
                }
                if (statusSuppliers.containsKey(uuid)
                        && statusSuppliers
                                .get(uuid)
                                .get()
                                .equals(GameServerDto.GameServerStatus.STOPPING)) {
                    statusListeners.forEach(
                            l -> l.accept(GameServerStatusUpdateEventType.STOPPED, uuid));
                } else {
                    statusListeners.forEach(
                            l -> l.accept(GameServerStatusUpdateEventType.FAILED, uuid));
                    remove(uuid);
                }
                break;
            default:
                log.warn("Received docker event with unexpected event action: {}", eventName);
        }
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
        Optional<Container> container = findContainer(serverConfig);

        if (container.isPresent()) {
            if (container.get().getState().equals("running")) {
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

        String image = buildImageName(serverConfig);
        String containerName = containerName(serverConfig);

        ensureImagePresent(
                serverConfig,
                image,
                progressListener,
                statusUpdater,
                imagePullStartCallback,
                imagePullEndCallback);

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

        statusSuppliers.put(serverConfig.getUuid(), gameServerStatusSupplier);
        try {
            client.startContainerCmd(response.getId()).exec();
        } catch (InternalServerErrorException e) {
            client.removeContainerCmd(response.getId()).withForce(true).exec();
            throw new InternalServiceStartException(e);
        }
    }

    @Override
    public void stopAndRemove(GameServerEntity serverConfig) {
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
        remove(serverConfig);
    }

    public void remove(GameServerEntity serverConfig) {
        remove(serverConfig.getUuid());
    }

    public void remove(String uuid) {
        findContainer(uuid)
                .ifPresent(
                        container -> {
                            client.removeContainerCmd(container.getId()).withForce(true).exec();
                        });
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
        return findContainer(serverConfig.getUuid());
    }

    private Optional<Container> findContainer(String serverUuid) {
        String nameToMatch = String.format("/%s", containerName(serverUuid));

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
        return containerName(serverConfig.getUuid());
    }

    private String containerName(String uuid) {
        return String.format("cosy-%s", uuid);
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
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback)
            throws DockerPullImageException {
        // TODO: refactor
        boolean exists =
                client.listImagesCmd().withImageNameFilter(image).exec().stream()
                        .anyMatch(
                                img -> {
                                    String[] tags = img.getRepoTags();
                                    return tags != null && Arrays.asList(tags).contains(image);
                                });

        if (!exists) {
            imagePullStartCallback.accept(null);
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
                imagePullEndCallback.accept(null);
            } catch (Exception e) {
                statusUpdater.accept(GameServerDto.GameServerStatus.FAILED);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new DockerPullImageException(image);
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
    public Optional<Metric> collectMetric(GameServerEntity gameServer) throws InterruptedException {
        Optional<Container> containerOpt = findContainer(gameServer);
        if (containerOpt.isEmpty()) {
            return Optional.empty();
        }

        String containerUuid = containerOpt.get().getId();

        InspectContainerResponse container = client.inspectContainerCmd(containerUuid).exec();

        if (Boolean.FALSE.equals(container.getState().getRunning())) {
            return Optional.empty();
        }

        AtomicReference<Metric> statsRef = new AtomicReference<>();
        client.statsCmd(containerUuid)
                .exec(
                        new ResultCallback.Adapter<Statistics>() {
                            @Override
                            public void onNext(Statistics statistics) {
                                Metric stats = statsMapper.mapStats(statistics);
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

        return Optional.ofNullable(statsRef.get());
    }
}
