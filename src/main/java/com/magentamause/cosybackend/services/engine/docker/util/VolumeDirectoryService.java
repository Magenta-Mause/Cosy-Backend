package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class VolumeDirectoryService {

    private final EngineProperties engineProperties;

    public void assertVolumeDirectoriesExist(GameServerEntity server) {
        if (server.getVolumeMounts() == null || server.getVolumeMounts().isEmpty()) {
            return;
        }

        Path base = volumeBaseDir();

        for (VolumeMountConfiguration vm : server.getVolumeMounts()) {
            String volumeUuid = vm.getUuid();
            if (volumeUuid == null || volumeUuid.isBlank()) {
                // Should not happen if server is saved, but guard anyway.
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Volume mount uuid missing after save");
            }

            assertValidUuidOrThrow(volumeUuid, HttpStatus.BAD_REQUEST, "Invalid volume uuid");

            Path dir = base.resolve(volumeUuid).normalize();
            if (!dir.startsWith(base)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid volume uuid");
            }

            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to create volume directory: " + dir,
                        e);
            }
        }
    }

    public void deleteVolumeDirectories(GameServerEntity server) {
        if (server.getVolumeMounts() == null || server.getVolumeMounts().isEmpty()) {
            return;
        }

        Path base = volumeBaseDir();

        List<VolumeMountConfiguration> volumeMounts = server.getVolumeMounts();
        for (VolumeMountConfiguration vm : volumeMounts) {
            String id = vm.getUuid();
            if (id == null || id.isBlank()) {
                log.warn(
                        "Skipping volume with null or blank UUID for server: {}", server.getUuid());
                continue;
            }

            if (!isValidUuid(id)) {
                log.warn(
                        "Skipping volume with invalid UUID '{}' for server: {}",
                        id,
                        server.getUuid());
                continue;
            }

            Path dir = base.resolve(id).normalize();
            if (!dir.startsWith(base)) {
                log.warn(
                        "Skipping volume with invalid path '{}' for server: {}",
                        dir,
                        server.getUuid());
                continue;
            }

            try {
                if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                    deleteDirectoryRecursive(dir);
                }
            } catch (IOException e) {
                log.error(
                        "Failed to delete volume directory '{}' for server: {}",
                        dir,
                        server.getUuid(),
                        e);
            }
        }
    }

    public void deleteVolumeDirectoriesByUuids(List<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }

        Path base = volumeBaseDir();

        for (String id : uuids) {
            if (id == null || id.isBlank()) {
                log.warn("Skipping null or blank UUID in selective volume cleanup");
                continue;
            }

            if (!isValidUuid(id)) {
                log.warn("Skipping invalid UUID '{}' in selective volume cleanup", id);
                continue;
            }

            Path dir = base.resolve(id).normalize();
            if (!dir.startsWith(base)) {
                log.warn("Skipping invalid path '{}' in selective volume cleanup", dir);
                continue;
            }

            try {
                if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                    deleteDirectoryRecursive(dir);
                }
            } catch (IOException e) {
                log.error("Failed to delete volume directory '{}' during selective cleanup", dir, e);
            }
        }
    }

    private static void assertValidUuidOrThrow(String value, HttpStatus status, String message) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(status, message);
        }
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void deleteDirectoryRecursive(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            Files.delete(path);
            return;
        }

        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(
                                p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        } else {
            Files.delete(path);
        }
    }

    private Path volumeBaseDir() {
        String baseDir = engineProperties.docker().volumeDirectory();
        if (baseDir == null || baseDir.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "cosy.engine.docker.volume-directory is not configured");
        }
        return Paths.get(baseDir).toAbsolutePath().normalize();
    }
}
