package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.services.engine.config.EngineProperties;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerMountService {
    private final GameServerService gameServerService;
    private final ConcurrentHashMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();
    private final EngineProperties engineProperties;

    private ReadWriteLock lockForServer(String serverUuid) {
        return locks.computeIfAbsent(serverUuid, k -> new ReentrantReadWriteLock(true));
    }

    private <T> T withReadLock(String serverUuid, java.util.concurrent.Callable<T> action) {
        Lock l = lockForServer(serverUuid).readLock();
        l.lock();
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            l.unlock();
        }
    }

    private void withWriteLock(String serverUuid, Runnable action) {
        Lock l = lockForServer(serverUuid).writeLock();
        l.lock();
        try {
            action.run();
        } finally {
            l.unlock();
        }
    }

    /**
     * Reads a bind mount filesystem by using the requested path as-is, selecting the bind mount by
     * matching the requested path against a volume's containerPath prefix. The part after the
     * containerPath is used to query inside the selected hostPath.
     */
    public GameServerFileSystemDto readBindMountFileSystem(
            String serverUuid, String requestedPath, int fetchDepth) {
        return withReadLock(
                serverUuid,
                () -> {
                    if (fetchDepth < 0) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "fetchDepth must be >= 0");
                    }

                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = resolved.requested();

                    if (!resolved.isRootRequest()) {
                        requireExists(requested, resolved.innerRelative());
                        requireRealPathInsideRoot(volumeRoot, requested, resolved.innerRelative());
                    }

                    if (Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
                        List<GameServerFileSystemDto.FileSystemObjectDto> entries =
                                listDirectory(requested, fetchDepth);
                        return GameServerFileSystemDto.builder()
                                .volumeUuid(resolved.volumeUuid())
                                .objects(entries)
                                .build();
                    }

                    GameServerFileSystemDto.FileSystemObjectDto fileNode = toNode(requested, 0);
                    return GameServerFileSystemDto.builder()
                            .volumeUuid(resolved.volumeUuid())
                            .objects(List.of(fileNode))
                            .build();
                });
    }

    /**
     * Reads a file from a bind mount volume by using the requested path as-is (container path +
     * optional subpath). The bind mount is selected by containerPath prefix match.
     */
    public byte[] readFileFromBindMountVolume(String serverUuid, String requestedPath) {
        return withReadLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = resolved.requested();

                    String cleanedInner =
                            requireNonBlank(
                                    cleanRelative(resolved.innerRelative()),
                                    "File path must not be empty");

                    requireExists(requested, cleanedInner);
                    requireNotDirectory(requested, cleanedInner);
                    requireRealPathInsideRoot(volumeRoot, requested, cleanedInner);
                    requireReadable(requested, cleanedInner);

                    long maxFileSize = 128L * 1024L * 1024L; // 128 MB
                    try {
                        long fileSize = Files.size(requested);
                        if (fileSize > maxFileSize) {
                            throw new ResponseStatusException(
                                    HttpStatus.PAYLOAD_TOO_LARGE,
                                    "File size exceeds maximum allowed size of 128MB");
                        }
                        return Files.readAllBytes(requested);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to read file: " + cleanedInner,
                                e);
                    }
                });
    }

    private record ResolvedBindMount(
            String volumeUuid,
            String containerPathNormalized,
            String innerRelative,
            boolean isRootRequest,
            Path volumeRoot,
            Path requested) {}

    /**
     * Selects the volume mount by checking whether the requested path starts with any mount's
     * containerPath (boundary-aware), then strips that prefix and resolves the remainder inside the
     * hostPath.
     */
    private ResolvedBindMount resolveBindMount(String serverUuid, String requestedPath) {
        GameServerEntity server = gameServerService.getGameServerById(serverUuid);

        String req = normalizeContainerLikePath(requestedPath);
        if (req.isBlank() || "/".equals(req)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path must not be empty");
        }

        VolumeMountConfiguration mount = findMountByContainerPathPrefix(server, serverUuid, req);

        String containerPathNorm = normalizeContainerLikePath(mount.getContainerPath());
        String inner = stripContainerPrefix(req, containerPathNorm); // may be "" (root)

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

    private Path requireVolumeRootFromMount(VolumeMountConfiguration mount) {
        String baseDir =
                Optional.ofNullable(engineProperties)
                        .map(EngineProperties::docker)
                        .map(EngineProperties.Docker::volumeDirectory)
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

        // basic hardening
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

        // Optional: still keep a friendly error if something is weird (file instead of
        // dir)
        if (!Files.isDirectory(volumeRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Mount root is not a directory: " + volumeRoot);
        }

        return volumeRoot;
    }

    /**
     * Finds the best matching mount for the requested path: - boundary-aware prefix match: "/data"
     * matches "/data" and "/data/..." but NOT "/database/..." - prefers the longest matching
     * containerPath (more specific mount wins)
     */
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
            return false; // avoid "match everything" mount definitions
        }
        if (requestedPathNormalized.equals(containerPathNormalized)) {
            return true;
        }
        // ensure boundary so "/data" doesn't match "/database"
        return requestedPathNormalized.startsWith(containerPathNormalized + "/");
    }

    /**
     * Strips the container path prefix and returns the remainder as a relative path without leading
     * "/". If requested equals containerPath, returns "".
     */
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

    /**
     * Normalizes a container-style path: - trims - replaces backslashes with slashes - ensures a
     * single leading "/" - removes trailing "/" (except for "/")
     */
    private String normalizeContainerLikePath(String input) {
        String s = (input == null) ? "" : input.trim();
        s = s.replace("\\", "/");
        if (s.isBlank()) return "";
        if (!s.startsWith("/")) s = "/" + s;
        while (s.contains("//")) s = s.replace("//", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private Path resolveInsideRoot(
            Path root, String relative, boolean requireNonBlank, String label) {
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

    private void requireExists(Path p, String cleaned) {
        requireExists(p, cleaned, "Path not found: ");
    }

    private void requireExists(Path p, String cleaned, String messagePrefix) {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, messagePrefix + cleaned);
        }
    }

    private void requireRealPathInsideRoot(Path root, Path requested, String cleanedForMessage) {
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

    private GameServerFileSystemDto.FileSystemObjectDto buildTree(Path root, int fetchDepth) {
        GameServerFileSystemDto.FileSystemObjectDto node = toNode(root, fetchDepth);

        if (fetchDepth <= 0 || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return node;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                node.getChildren().add(buildTree(child, fetchDepth - 1));
            }
        } catch (AccessDeniedException ade) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied", ade);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading", e);
        }

        node.getChildren()
                .sort(
                        Comparator.comparing(
                                        (GameServerFileSystemDto.FileSystemObjectDto n) ->
                                                n.getType()
                                                        != GameServerFileSystemDto.FileType
                                                                .DIRECTORY)
                                .thenComparing(
                                        GameServerFileSystemDto.FileSystemObjectDto::getName,
                                        String.CASE_INSENSITIVE_ORDER));

        return node;
    }

    private List<GameServerFileSystemDto.FileSystemObjectDto> listDirectory(
            Path dir, int fetchDepth) {
        if (fetchDepth < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fetchDepth must be >= 0");
        }

        List<GameServerFileSystemDto.FileSystemObjectDto> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                children.add(buildTree(child, Math.max(fetchDepth - 1, 0)));
            }
        } catch (AccessDeniedException ade) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied", ade);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading", e);
        }

        children.sort(
                Comparator.comparing(
                                (GameServerFileSystemDto.FileSystemObjectDto n) ->
                                        n.getType() != GameServerFileSystemDto.FileType.DIRECTORY)
                        .thenComparing(
                                GameServerFileSystemDto.FileSystemObjectDto::getName,
                                String.CASE_INSENSITIVE_ORDER));

        return children;
    }

    private String cleanRelative(String input) {
        String cleaned = (input == null) ? "" : input.trim();
        cleaned = cleaned.replace("\\", "/");
        if (cleaned.equals(".") || cleaned.equals("./")) cleaned = "";
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        return cleaned;
    }

    private GameServerFileSystemDto.FileSystemObjectDto toNode(Path p, int fetchDepth) {
        boolean isDir = Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS);
        Optional<Integer> perms = tryReadPermissions(p);
        Optional<Long> size = tryReadSize(p);

        return GameServerFileSystemDto.FileSystemObjectDto.builder()
                .fetchDepth(fetchDepth)
                .name(p.getFileName() != null ? p.getFileName().toString() : p.toString())
                .type(
                        isDir
                                ? GameServerFileSystemDto.FileType.DIRECTORY
                                : GameServerFileSystemDto.FileType.FILE)
                .permissions(perms)
                .size(size)
                .children(new ArrayList<>())
                .build();
    }

    /**
     * Returns a Unix-like permission bitmask (e.g. 0755 -> 493 decimal) when supported. Optional is
     * empty if permissions could not be fetched.
     */
    private Optional<Integer> tryReadPermissions(Path p) {
        try {
            PosixFileAttributes attrs =
                    Files.readAttributes(p, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> perms = attrs.permissions();
            return Optional.of(toUnixMode(perms));
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private int toUnixMode(Set<PosixFilePermission> perms) {
        int mode = 0;
        if (perms.contains(PosixFilePermission.OWNER_READ)) mode |= 0400;
        if (perms.contains(PosixFilePermission.OWNER_WRITE)) mode |= 0200;
        if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) mode |= 0100;

        if (perms.contains(PosixFilePermission.GROUP_READ)) mode |= 0040;
        if (perms.contains(PosixFilePermission.GROUP_WRITE)) mode |= 0020;
        if (perms.contains(PosixFilePermission.GROUP_EXECUTE)) mode |= 0010;

        if (perms.contains(PosixFilePermission.OTHERS_READ)) mode |= 0004;
        if (perms.contains(PosixFilePermission.OTHERS_WRITE)) mode |= 0002;
        if (perms.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= 0001;
        return mode;
    }

    private void requireReadable(Path p, String cleaned) {
        if (!Files.isReadable(p)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No read permission for file: " + cleaned);
        }
    }

    private void requireNotDirectory(Path p, String cleaned) {
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Path is a directory: " + cleaned);
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private Optional<Long> tryReadSize(Path p) {
        try {
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.of(0L);
            }
            return Optional.of(Files.size(p));
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }
}
