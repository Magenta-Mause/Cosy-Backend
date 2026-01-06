package com.magentamause.cosybackend.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.engine.EngineManager;
import com.magentamause.cosybackend.engine.config.EngineProperties.Docker;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.exceptions.ServerAlreadyStoppedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DockerEngineManager implements EngineManager {

    private final Docker config;
    private final DockerClient client;

    public DockerEngineManager(DockerClient client, Docker config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public List<Integer> start(GameServerConfigurationEntity serverConfig) {
        Optional<Container> existing = findContainer(serverConfig);

        if (existing.isPresent()) {
            if (!existing.get().getState().equals("running")) {
                client.startContainerCmd(existing.get().getId()).exec();
            }
            return getInstancePorts(serverConfig);
        }

        String image = buildImageName(serverConfig);
        String containerName = containerName(serverConfig);

        ensureImagePresent(image);

        CreateContainerResponse response =
                client.createContainerCmd(image)
                        .withName(containerName)
                        .withCmd(serverConfig.getDockerExecutionCommand())
                        .withEnv(mapEnvironment(serverConfig.getEnvironmentVariables()))
                        .withExposedPorts(mapExposedPorts(serverConfig.getPortMappings()))
                        .withHostConfig(buildHostConfig(serverConfig))
                        .exec();

        client.startContainerCmd(response.getId()).exec();
        return getInstancePorts(serverConfig);
    }

    @Override
    public void stop(GameServerConfigurationEntity serverConfig) {
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
    public GameServerStatusDto status(GameServerConfigurationEntity serverConfig) {
        Optional<Container> container = findContainer(serverConfig);
        if (container.isEmpty()) {
            return GameServerStatusDto.builder().status(GameServerStatusDto.GameServerStatus.NotFound).build();
        }

        String phase = container.get().getStatus();

        return GameServerStatusDto.builder().status(GameServerStatusDto.GameServerStatus.Found).phase(phase).build();
    }

    private Optional<Container> findContainer(GameServerConfigurationEntity serverConfig) {
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

    private String buildImageName(GameServerConfigurationEntity serverConfig) {
        String tag = serverConfig.getDockerImageTag();
        return (tag == null || tag.isBlank())
                ? serverConfig.getDockerImageName()
                : String.format("%s:%s", serverConfig.getDockerImageName(), tag);
    }

    private String containerName(GameServerConfigurationEntity serverConfig) {
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

    private HostConfig buildHostConfig(GameServerConfigurationEntity serverConfig) {
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

    private static ExposedPort portMappingToExposedPort(PortMapping pm) {
        return switch (pm.getProtocol()) {
            case TCP -> ExposedPort.tcp(pm.getContainerPort());
            case UDP -> ExposedPort.udp(pm.getContainerPort());
            default -> throw new IllegalArgumentException("Unknown port type: " + pm.getProtocol());
        };
    }

    private void ensureImagePresent(String image) {
        boolean exists =
                client.listImagesCmd().withImageNameFilter(image).exec().stream()
                        .anyMatch(
                                img -> {
                                    String[] tags = img.getRepoTags();
                                    return tags != null && Arrays.asList(tags).contains(image);
                                });

        if (!exists) {
            try {
                client.pullImageCmd(image).start().awaitCompletion();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        String.format("Interrupted while pulling Docker image %s", image), e);
            }
        }
    }

    private List<Integer> getInstancePorts(GameServerConfigurationEntity serverConfig) {
        return Optional.ofNullable(serverConfig.getPortMappings()).orElse(List.of()).stream()
                .map(PortMapping::getInstancePort)
                .collect(Collectors.toList());
    }
}
