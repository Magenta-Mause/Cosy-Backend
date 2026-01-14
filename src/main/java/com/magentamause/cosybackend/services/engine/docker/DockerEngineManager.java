package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.engine.EngineManager;
import com.magentamause.cosybackend.services.engine.config.EngineProperties.Docker;
import com.magentamause.cosybackend.websockets.GameServerStatusPublisher;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class DockerEngineManager implements EngineManager, Closeable {

    private final Docker config;
    private final DockerClient client;
    private final GameServerRepository gameServerRepository;
    private final GameServerStatusPublisher statusPublisher;

    private static ExposedPort portMappingToExposedPort(PortMapping pm) {
        return switch (pm.getProtocol()) {
            case TCP -> ExposedPort.tcp(pm.getContainerPort());
            case UDP -> ExposedPort.udp(pm.getContainerPort());
            default -> throw new IllegalArgumentException("Unknown port type: " + pm.getProtocol());
        };
    }

    @Override
    public List<Integer> start(GameServerEntity serverConfig) {
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

        ensureImagePresent(serverConfig, image);

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

    private void ensureImagePresent(GameServerEntity serverConfig, String image) {
        boolean exists =
                client.listImagesCmd().withImageNameFilter(image).exec().stream()
                        .anyMatch(
                                img -> {
                                    String[] tags = img.getRepoTags();
                                    return tags != null && Arrays.asList(tags).contains(image);
                                });

        if (!exists) {
            updateStatus(serverConfig, GameServerEntity.GameServerStatus.PULLING_IMAGE);
            try {
                client.pullImageCmd(image).start().awaitCompletion();
            } catch (Exception e) {
                updateStatus(serverConfig, GameServerEntity.GameServerStatus.FAILED);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException(
                        String.format("Failed to pull Docker image %s", image), e);
            }
        }
    }

    private void updateStatus(
            GameServerEntity serverConfig, GameServerEntity.GameServerStatus status) {
        serverConfig.setStatus(status);
        gameServerRepository.save(serverConfig);
        statusPublisher.publishStatus(serverConfig.getUuid(), status);
    }

    private List<Integer> getInstancePorts(GameServerEntity serverConfig) {
        return Optional.ofNullable(serverConfig.getPortMappings()).orElse(List.of()).stream()
                .map(PortMapping::getInstancePort)
                .collect(Collectors.toList());
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                throw new IOException("Failed to close DockerClient", e);
            }
        }
    }
}

