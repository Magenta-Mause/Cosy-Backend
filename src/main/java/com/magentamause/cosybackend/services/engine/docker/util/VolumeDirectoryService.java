package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.GameServerEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class VolumeDirectoryService {

    private final EngineProperties engineProperties;

    public void assertVolumeDirectoriesExist(GameServerEntity server) {
        if (server.getVolumeMounts() == null || server.getVolumeMounts().isEmpty()) {
            return;
        }

        Path base = volumeBaseDir();

        for (var vm : server.getVolumeMounts()) {
            String id = vm.getUuid();
            if (id == null || id.isBlank()) {
                // Should not happen if server is saved, but guard anyway.
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Volume mount uuid missing after save");
            }
            if (id.contains("/") || id.contains("\\") || id.contains("..")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid volume uuid");
            }

            Path dir = base.resolve(id).normalize();
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

        for (var vm : server.getVolumeMounts()) {
            String id = vm.getUuid();
            if (id == null || id.isBlank()) {
                continue;
            }

            Path dir = base.resolve(id).normalize();
            if (!dir.startsWith(base)) {
                continue;
            }

            try {
                if (Files.exists(dir)) {
                    deleteDirectoryRecursive(dir);
                }
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to delete volume directory: " + dir,
                        e);
            }
        }
    }

    private void deleteDirectoryRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(
                                p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        throw new RuntimeException(
                                                "Failed to delete: " + p, e);
                                    }
                                });
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
