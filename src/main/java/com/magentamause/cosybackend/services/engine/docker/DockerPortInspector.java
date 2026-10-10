package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.services.engine.PublishedPort;
import com.magentamause.cosybackend.services.engine.docker.util.DockerContainerNameResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Reads the host ports that running Docker containers currently publish. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerPortInspector {

    private final DockerClient client;
    private final DockerContainerNameResolver containerNameResolver;

    /**
     * Lists every host port published by a running container.
     *
     * <p>Only running containers are listed: a stopped container releases its bindings, so its
     * ports are free to take. Docker reports one entry per bound host interface (IPv4 and IPv6), so
     * the same port can appear more than once — callers that only compare port numbers do not care,
     * and de-duplicating here would hide nothing useful.
     *
     * <p>Fails open: when the daemon cannot be reached this returns an empty list rather than
     * throwing. The check exists to turn an unhelpful container start failure into a clear message;
     * making it a hard gate would mean a hiccup in the daemon blocks starts that Docker itself
     * would happily accept — and a start attempt against an unreachable daemon fails on its own
     * with a proper error anyway.
     */
    public List<PublishedPort> listPublishedHostPorts() {
        try {
            return client.listContainersCmd().exec().stream()
                    .flatMap(container -> toPublishedPorts(container).stream())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Could not read published ports from the Docker daemon", e);
            return List.of();
        }
    }

    private List<PublishedPort> toPublishedPorts(Container container) {
        String containerName = resolveName(container);
        String gameServerUuid = resolveGameServerUuid(containerName);

        return Arrays.stream(Optional.ofNullable(container.getPorts()).orElse(new ContainerPort[0]))
                .filter(port -> port.getPublicPort() != null)
                .flatMap(
                        port ->
                                toProtocol(port.getType()).stream()
                                        .map(
                                                protocol ->
                                                        new PublishedPort(
                                                                port.getPublicPort(),
                                                                protocol,
                                                                containerName,
                                                                gameServerUuid)))
                .toList();
    }

    /**
     * Docker also knows SCTP, which Cosy cannot express as a {@link PortMapping} — such a binding
     * can never collide with a Cosy port mapping and is dropped.
     */
    private Optional<PortMapping.PortProtocol> toProtocol(String type) {
        if (type == null) {
            return Optional.empty();
        }
        return switch (type.toLowerCase()) {
            case "tcp" -> Optional.of(PortMapping.PortProtocol.TCP);
            case "udp" -> Optional.of(PortMapping.PortProtocol.UDP);
            default -> Optional.empty();
        };
    }

    private String resolveName(Container container) {
        return Arrays.stream(Optional.ofNullable(container.getNames()).orElse(new String[0]))
                .findFirst()
                .map(name -> name.startsWith("/") ? name.substring(1) : name)
                .orElseGet(container::getId);
    }

    private String resolveGameServerUuid(String containerName) {
        if (!containerName.startsWith(containerNameResolver.getPrefix())) {
            return null;
        }
        return containerNameResolver.extractUuidFromContainerName(containerName);
    }
}
