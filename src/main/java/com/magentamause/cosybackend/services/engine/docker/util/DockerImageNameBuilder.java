package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;

/** Utility class for building Docker image names with optional tags. */
public final class DockerImageNameBuilder {

    private DockerImageNameBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String buildImageName(GameServerEntity serverConfig) {
        String tag = serverConfig.getDockerImageTag();
        return (tag == null || tag.isBlank())
                ? serverConfig.getDockerImageName()
                : String.format("%s:%s", serverConfig.getDockerImageName(), tag);
    }
}
