package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
class GameServerMountResolver {

    private final GameServerService gameServerService;
    private final EngineProperties engineProperties;

    ResolvedBindMount resolveBindMount(String serverUuid, String requestedPath) {
        GameServerEntity server = gameServerService.getOrThrow(serverUuid);

        String req = normalizeContainerLikePath(requestedPath);
        if (req.isBlank() || "/".equals(req)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path must not be empty");
        }

        VolumeMountConfiguration mount = findMountByContainerPathPrefix(server, serverUuid, req);

        String containerPathNorm = normalizeContainerLikePath(mount.getContainerPath());
        String inner = stripContainerPrefix(req, containerPathNorm);

        Path volumeRoot = requireVolumeRootFromMount(mount);

        boolean isRootRequest = inner.isBlank();
        Path requested =
                isRootRequest ? volumeRoot : resolveInsideRoot(volumeRoot, inner, true, "Path");
        if (!isRootRequest) {
            if (!requested.startsWith(volumeRoot)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Path escapes volume root");
            }
        }

        String volumeUuid = mount.getUuid();

        return new ResolvedBindMount(
                volumeUuid, containerPathNorm, inner, isRootRequest, volumeRoot, requested);
    }

    Path requireVolumeRootFromMount(VolumeMountConfiguration mount) {
        String baseDir =
                Optional.ofNullable(engineProperties)
                        .map(EngineProperties::docker)
                        .map(EngineProperties.Docker::inBackendVolumeMountPath)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "cosy.engine.docker.volume-directory is not configured"));

        String uuid =
                Optional.ofNullable(mount.getUuid())
                        .filter(s -> !s.isBlank())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Volume mount does not have a uuid"));

        if (uuid.contains("/") || uuid.contains("\\") || uuid.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid volume uuid");
        }

        Path volumeRoot = Paths.get(baseDir).resolve(uuid).normalize().toAbsolutePath();

        try {
            Files.createDirectories(volumeRoot);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create mount directory: " + volumeRoot,
                    e);
        }

        if (!Files.isDirectory(volumeRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mount root is not a directory: " + volumeRoot);
        }

        return volumeRoot;
    }

    private VolumeMountConfiguration findMountByContainerPathPrefix(
            GameServerEntity server, String serverUuid, String requestedPathNormalized) {

        List<VolumeMountConfiguration> mounts =
                Optional.ofNullable(server.getVolumeMounts()).orElse(List.of());

        return mounts.stream()
                .filter(
                        vm -> {
                            String cp = normalizeContainerLikePath(vm.getContainerPath());
                            return isBoundaryAwarePrefixMatch(requestedPathNormalized, cp);
                        })
                .max(
                        Comparator.comparingInt(
                                vm -> normalizeContainerLikePath(vm.getContainerPath()).length()))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No volume mount matching "
                                                + requestedPathNormalized
                                                + " found on server "
                                                + serverUuid));
    }

    private boolean isBoundaryAwarePrefixMatch(
            String requestedPathNormalized, String containerPathNormalized) {
        if (containerPathNormalized.isBlank() || "/".equals(containerPathNormalized)) {
            return false;
        }
        if (requestedPathNormalized.equals(containerPathNormalized)) {
            return true;
        }
        return requestedPathNormalized.startsWith(containerPathNormalized + "/");
    }

    private String stripContainerPrefix(
            String requestedPathNormalized, String containerPathNormalized) {
        if (requestedPathNormalized.equals(containerPathNormalized)) {
            return "";
        }
        if (requestedPathNormalized.startsWith(containerPathNormalized + "/")) {
            String remainder =
                    requestedPathNormalized.substring((containerPathNormalized + "/").length());
            return cleanRelative(remainder);
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Path does not start with container path");
    }

    String normalizeContainerLikePath(String input) {
        String s = (input == null) ? "" : input.trim();
        s = s.replace("\\", "/");
        if (s.isBlank()) return "";
        if (!s.startsWith("/")) s = "/" + s;
        while (s.contains("//")) s = s.replace("//", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    Path resolveInsideRoot(Path root, String relative, boolean requireNonBlank, String label) {
        String cleaned = cleanRelative(relative);
        if (requireNonBlank && cleaned.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must not be empty");
        }

        Path requested = root.resolve(cleaned).normalize();
        if (!requested.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path escapes volume root");
        }
        return requested;
    }

    String cleanRelative(String input) {
        String cleaned = (input == null) ? "" : input.trim();
        cleaned = cleaned.replace("\\", "/");
        if (cleaned.equals(".") || cleaned.equals("./")) cleaned = "";
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        return cleaned;
    }

    static Path safeRelativePath(String input, String label, boolean allowEmpty) {
        String s = (input == null) ? "" : input.trim();
        s = s.replace("\\", "/");
        while (s.startsWith("/")) s = s.substring(1);

        Path rel = Paths.get(s).normalize();

        if (rel.toString().isBlank()) {
            if (allowEmpty) return rel;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must not be empty");
        }

        if (rel.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be relative");
        }

        for (Path part : rel) {
            if ("..".equals(part.toString())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, label + " must not contain '..'");
            }
        }

        return rel;
    }

    // ===== Guards =====

    void requireExists(Path p, String cleaned) {
        requireExists(p, cleaned, "Path not found: ");
    }

    void requireExists(Path p, String cleaned, String messagePrefix) {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, messagePrefix + cleaned);
        }
    }

    void requireNotExists(Path p, String cleaned, String messagePrefix) {
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, messagePrefix + cleaned);
        }
    }

    void requireRealPathInsideRoot(Path root, Path requested, String cleanedForMessage) {
        try {
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realRequested = requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realRequested.startsWith(realRoot)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Path escapes volume root");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Path not accessible: " + cleanedForMessage, e);
        }
    }

    void requireReadable(Path p, String cleaned) {
        if (!Files.isReadable(p)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No read permission for file: " + cleaned);
        }
    }

    void requireWritable(Path p, String message) {
        if (!Files.isWritable(p)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    void requireNotDirectory(Path p, String cleaned) {
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Path is a directory: " + cleaned);
        }
    }

    void requireDirectory(Path p, String cleaned, String msgPrefix) {
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgPrefix + cleaned);
        }
    }

    Path requireParentExistsAndDirectory(Path requested, String cleaned, String notFoundPrefix) {
        Path parent = requested.getParent();
        if (parent == null || !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundPrefix + cleaned);
        }
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Parent path is not a directory: " + cleaned);
        }
        return parent;
    }

    static Path requireParent(Path p, String message) {
        Path parent = p.getParent();
        if (parent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return parent;
    }

    static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    static void requireTargetNotInsideSourceDir(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Path sourceReal = source.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize();

        Path targetParent = requireParent(target, "Invalid newPath");
        Path targetParentReal = targetParent.toRealPath().normalize();
        Path targetAbs = targetParentReal.resolve(target.getFileName()).normalize();

        if (targetAbs.equals(sourceReal) || targetAbs.startsWith(sourceReal)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot move a directory into itself or its subdirectory");
        }
    }

    // ===== Secure directory stream operations =====

    SecureRoot openRootDirectoryStream(Path volumeRoot) {
        try {
            DirectoryStream<Path> ds = Files.newDirectoryStream(volumeRoot);
            if (ds instanceof SecureDirectoryStream<Path> sds) {
                return new SecureRoot(sds, true);
            }
            ds.close();
            return new SecureRoot(null, false);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to open volume root", e);
        }
    }

    SecureTarget resolveSecureNoSymlink(SecureDirectoryStream<Path> root, Path rel, String label)
            throws IOException {

        int n = rel.getNameCount();
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must not be empty");
        }

        List<DirectoryStream<Path>> opened = new ArrayList<>();
        SecureDirectoryStream<Path> cur = root;

        try {
            for (int i = 0; i < n - 1; i++) {
                Path part = rel.getName(i);

                BasicFileAttributes attrs =
                        cur.getFileAttributeView(
                                        part,
                                        BasicFileAttributeView.class,
                                        LinkOption.NOFOLLOW_LINKS)
                                .readAttributes();

                if (!attrs.isDirectory()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Parent component is not a directory: " + part);
                }

                DirectoryStream<Path> next =
                        cur.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
                opened.add(next);

                if (!(next instanceof SecureDirectoryStream<Path> nextSecure)) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "SecureDirectoryStream lost while resolving");
                }

                cur = nextSecure;
            }

            return new SecureTarget(cur, rel.getFileName(), opened);

        } catch (Throwable t) {
            for (int i = opened.size() - 1; i >= 0; i--) {
                try {
                    opened.get(i).close();
                } catch (Throwable ignored) {
                }
            }
            throw t;
        }
    }

    void closeQuietly(List<DirectoryStream<Path>> streams) {
        for (int i = streams.size() - 1; i >= 0; i--) {
            try {
                streams.get(i).close();
            } catch (IOException ignored) {
            }
        }
    }
}
