package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.entities.GameServerEntity;

/** Utility class for resolving Docker container names following the "cosy-" naming convention. */
public final class DockerContainerNameResolver {

    private static final String CONTAINER_NAME_PREFIX = "cosy-";

    private DockerContainerNameResolver() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String containerName(GameServerEntity serverConfig) {
        return containerName(serverConfig.getUuid());
    }

    public static String containerName(String uuid) {
        return String.format("%s%s", CONTAINER_NAME_PREFIX, uuid);
    }

    public static String containerNameWithSlash(String uuid) {
        return String.format("/%s", containerName(uuid));
    }

    public static String extractUuidFromContainerName(String containerName) {
        if (containerName != null && containerName.startsWith(CONTAINER_NAME_PREFIX)) {
            return containerName.substring(CONTAINER_NAME_PREFIX.length());
        }
        throw new IllegalArgumentException("Invalid container name: " + containerName);
    }
}
