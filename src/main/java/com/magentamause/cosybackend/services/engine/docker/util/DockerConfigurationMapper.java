package com.magentamause.cosybackend.services.engine.docker.util;

import com.github.dockerjava.api.model.ExposedPort;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Utility class for mapping domain entities to Docker API model objects. */
public final class DockerConfigurationMapper {

    private DockerConfigurationMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<String> mapEnvironment(List<EnvironmentVariableConfiguration> envs) {
        return Optional.ofNullable(envs).orElse(List.of()).stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public static List<ExposedPort> mapExposedPorts(List<PortMapping> ports) {
        return Optional.ofNullable(ports).orElse(List.of()).stream()
                .map(DockerConfigurationMapper::portMappingToExposedPort)
                .distinct()
                .collect(Collectors.toList());
    }

    public static ExposedPort portMappingToExposedPort(PortMapping pm) {
        return switch (pm.getProtocol()) {
            case TCP -> ExposedPort.tcp(pm.getContainerPort());
            case UDP -> ExposedPort.udp(pm.getContainerPort());
            default -> throw new IllegalArgumentException("Unknown port type: " + pm.getProtocol());
        };
    }
}
