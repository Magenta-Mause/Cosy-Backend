package com.magentamause.cosybackend.services.engine.docker;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Service for resolving and creating volume mount paths on the host system. */
@Component
@RequiredArgsConstructor
public class DockerVolumePathResolver {

    private final EngineProperties engineProperties;

    public String resolveAndEnsureVolumeHostPath(VolumeMountConfiguration volumeConfig) {
        String baseDir = getBaseDirectory();
        String uuid = getValidatedUuid(volumeConfig);

        Path mountPath = Paths.get(baseDir).resolve(uuid).normalize();

        ensureDirectoryExists(mountPath);

        return mountPath.toAbsolutePath().toString();
    }

    private String getBaseDirectory() {
        return Optional.ofNullable(engineProperties)
                .map(EngineProperties::docker)
                .map(EngineProperties.Docker::volumeDirectory)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "cosy.engine.docker.volume-directory is not configured"));
    }

    private String getValidatedUuid(VolumeMountConfiguration volumeConfig) {
        String uuid =
                Optional.ofNullable(volumeConfig.getUuid())
                        .filter(s -> !s.isBlank())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "VolumeMountConfiguration.uuid is null/blank; cannot resolve host path"));

        if (uuid.contains("/") || uuid.contains("\\") || uuid.contains("..")) {
            throw new IllegalArgumentException("Invalid volume uuid: " + uuid);
        }

        return uuid;
    }

    private void ensureDirectoryExists(Path mountPath) {
        try {
            Files.createDirectories(mountPath);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create volume mount directory: " + mountPath, e);
        }
    }
}
