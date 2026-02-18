package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Service for resolving Docker container names with a configurable prefix. */
@Component
@RequiredArgsConstructor
public class DockerContainerNameResolver {

    private final EngineProperties engineProperties;

    public String containerName(GameServerEntity serverConfig) {
        return containerName(serverConfig.getUuid());
    }

    public String containerName(String uuid) {
        return String.format("%s%s", getPrefix(), uuid);
    }

    public String containerNameWithSlash(String uuid) {
        return String.format("/%s", containerName(uuid));
    }

    public String extractUuidFromContainerName(String containerName) {
        String prefix = getPrefix();
        if (containerName != null && containerName.startsWith(prefix)) {
            return containerName.substring(prefix.length());
        }
        throw new IllegalArgumentException("Invalid container name: " + containerName);
    }

    public String getPrefix() {
        return engineProperties.docker().containerNamePrefix();
    }
}
