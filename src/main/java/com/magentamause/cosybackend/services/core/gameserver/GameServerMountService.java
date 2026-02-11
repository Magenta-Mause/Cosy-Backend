package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private record ResolvedBindMount(
            String volumeUuid,
            String containerPathNormalized,
            String innerRelative,
            boolean isRootRequest,
            Path volumeRoot,
            Path requested) {}

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

    /**
     * Creates a directory at the given container-style requested path. The mount is selected by
     * containerPath prefix match.
     */
    public void createDirectoryInBindMountVolume(String serverUuid, String requestedPath) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = resolved.requested();

                    String cleaned =
                            requireNonBlank(
                                    cleanRelative(resolved.innerRelative()),
                                    "Directory path must not be empty");

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to create volume root");
                    }

                    Path parent =
                            requireParentExistsAndDirectory(
                                    requested, cleaned, "Parent directory does not exist: ");
                    requireRealPathInsideRoot(volumeRoot, parent, cleaned);

                    requireWritable(parent, "No permission to create directory in: " + parent);
                    requireNotExists(requested, cleaned, "Directory already exists: ");

                    try {
                        Files.createDirectory(requested);
                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied while creating directory", e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to create directory: " + cleaned,
                                e);
                    }
                });
    }

    /**
     * Uploads (writes) a file to the given container-style requested path. Creates/overwrites the
     * file atomically where supported.
     */
    public void uploadFileToBindMountVolume(
            String serverUuid, String requestedPath, byte[] fileContent) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = resolved.requested();

                    String cleaned =
                            requireNonBlank(
                                    cleanRelative(resolved.innerRelative()),
                                    "File path must not be empty");

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "File path must not be volume root");
                    }

                    Path parent =
                            requireParentExistsAndDirectory(
                                    requested, cleaned, "Parent directory does not exist: ");
                    requireRealPathInsideRoot(volumeRoot, parent, cleaned);

                    requireWritable(parent, "No write permission for directory: " + parent);

                    if (Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
                        requireNotDirectory(requested, cleaned);
                        requireWritable(requested, "No write permission for file: " + cleaned);
                    }

                    Path tempFile = null;
                    try {
                        tempFile =
                                Files.createTempFile(
                                        parent, requested.getFileName().toString(), ".upload");
                        Files.write(tempFile, fileContent);

                        try {
                            Files.move(
                                    tempFile,
                                    requested,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.ATOMIC_MOVE);
                            tempFile = null; // ownership transferred, don't delete in finally
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(tempFile, requested, StandardCopyOption.REPLACE_EXISTING);
                            tempFile = null; // same here
                        }

                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied while writing file", e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to write file: " + cleaned,
                                e);
                    } finally {
                        if (tempFile != null) {
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (IOException cleanupEx) {
                                log.warn("Failed to cleanup temp file: " + tempFile.toString());
                            }
                        }
                    }
                });
    }

    /**
     * Renames/moves an object inside a mount. Both paths are container-style. Cross-mount moves are
     * rejected.
     */
    public void renameInBindMountVolume(
            String serverUuid, String oldRequestedPath, String newRequestedPath) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount oldResolved = resolveBindMount(serverUuid, oldRequestedPath);
                    ResolvedBindMount newResolved = resolveBindMount(serverUuid, newRequestedPath);

                    Path sourceRoot = oldResolved.volumeRoot();
                    Path targetRoot = newResolved.volumeRoot();

                    Path source = oldResolved.requested();
                    Path target = newResolved.requested();

                    String oldClean =
                            requireNonBlank(
                                    cleanRelative(oldResolved.innerRelative()),
                                    "oldPath must not be empty");
                    String newClean =
                            requireNonBlank(
                                    cleanRelative(newResolved.innerRelative()),
                                    "newPath must not be empty");

                    if (oldResolved.isRootRequest() || newResolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Renaming volume root is not allowed");
                    }

                    requireExists(source, oldClean);

                    if (Files.isSymbolicLink(source)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Renaming symlinks is not allowed: " + oldClean);
                    }

                    Path sourceParent = requireParent(source, "Invalid oldPath");
                    Path targetParent = requireParent(target, "Invalid newPath");

                    requireExists(
                            targetParent, newClean, "Target parent directory does not exist: ");
                    requireDirectory(targetParent, newClean, "Target parent is not a directory: ");
                    requireNotExists(target, newClean, "Target already exists: ");

                    requireRealPathInsideRoot(sourceRoot, source, oldClean);
                    requireRealPathInsideRoot(sourceRoot, sourceParent, oldClean);

                    requireRealPathInsideRoot(targetRoot, targetParent, newClean);

                    try {
                        requireTargetNotInsideSourceDir(source, target);
                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied while validating rename", e);
                    } catch (NoSuchFileException e) {
                        throw new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Path not found", e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Failed to validate rename", e);
                    }

                    requireReadable(source, oldClean);
                    requireWritable(sourceParent, "No permission to modify: " + sourceParent);
                    requireWritable(targetParent, "No permission to write into: " + targetParent);

                    boolean sameMount = oldResolved.volumeUuid().equals(newResolved.volumeUuid());

                    try {
                        if (sameMount) {
                            try {
                                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                            } catch (AtomicMoveNotSupportedException e) {
                                Files.move(source, target);
                            }
                            return;
                        }

                        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                            Files.createDirectory(target);
                            copyDirectoryRecursiveNoSymlinks(source, target);

                            deleteDirectoryRecursive(source);
                        } else {
                            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);

                            // delete original
                            Files.delete(source);
                        }
                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied while renaming", e);
                    } catch (FileAlreadyExistsException e) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Target already exists: " + newClean, e);
                    } catch (NoSuchFileException e) {
                        throw new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Path not found", e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Failed to rename/move", e);
                    }
                });
    }

    /**
     * Recursively copies a directory tree from sourceDir to targetDir. Disallows symlinks anywhere
     * in the copied tree.
     *
     * <p>Preconditions: - sourceDir exists and is a directory (NOFOLLOW) - targetDir exists and is
     * a directory (NOFOLLOW) - target does not yet contain any of the copied children (enforced by
     * create + not-exists checks)
     */
    private void copyDirectoryRecursiveNoSymlinks(Path sourceDir, Path targetDir)
            throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(sourceDir)) {
            // Walk includes sourceDir itself; skip it (targetDir already created)
            walk.forEach(
                    p -> {
                        try {
                            if (p.equals(sourceDir)) return;

                            if (Files.isSymbolicLink(p)) {
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Cross-mount copy: symlinks are not allowed: " + p);
                            }

                            Path rel = sourceDir.relativize(p);
                            Path dest = targetDir.resolve(rel).normalize();

                            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                                if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
                                    // If it exists but isn't a directory, fail
                                    if (!Files.isDirectory(dest, LinkOption.NOFOLLOW_LINKS)) {
                                        throw new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "Target path already exists and is not a directory: "
                                                        + dest);
                                    }
                                } else {
                                    Files.createDirectory(dest);
                                }
                            } else {
                                // Copy file; do not overwrite
                                Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
                            }
                        } catch (ResponseStatusException rse) {
                            throw rse;
                        } catch (IOException ioe) {
                            throw new java.io.UncheckedIOException(ioe);
                        }
                    });
        } catch (java.io.UncheckedIOException uioe) {
            throw uioe.getCause();
        }
    }

    /** Deletes a file or directory (recursively) at the given container-style requested path. */
    public void deleteInBindMountVolume(String serverUuid, String requestedPath) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = resolved.requested();

                    String cleaned =
                            requireNonBlank(
                                    cleanRelative(resolved.innerRelative()),
                                    "Path must not be empty");

                    if (resolved.isRootRequest() || requested.equals(volumeRoot)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to delete volume root");
                    }

                    requireExists(requested, cleaned);

                    if (Files.isSymbolicLink(requested)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Deleting symlinks is not allowed: " + cleaned);
                    }

                    Path parent = requireParent(requested, "Invalid path: " + cleaned);

                    // Containment checks
                    requireRealPathInsideRoot(volumeRoot, requested, cleaned);
                    requireRealPathInsideRoot(volumeRoot, parent, cleaned);

                    requireWritable(parent, "No permission to delete from: " + parent);

                    try {
                        if (Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
                            deleteDirectoryRecursive(requested);
                        } else {
                            Files.delete(requested);
                        }
                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Access denied while deleting: " + cleaned,
                                e);
                    } catch (NoSuchFileException e) {
                        throw new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Path not found: " + cleaned, e);
                    } catch (DirectoryNotEmptyException e) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Directory not empty: " + cleaned, e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to delete: " + cleaned,
                                e);
                    }
                });
    }

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
                    HttpStatus.BAD_REQUEST, "Mount root is not a directory: " + volumeRoot);
        }

        return volumeRoot;
    }

    private static void requireTargetNotInsideSourceDir(Path source, Path target)
            throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Path sourceReal = source.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize();

        // Target might not exist; use parent real path + target file name.
        Path targetParent = requireParent(target, "Invalid newPath");
        Path targetParentReal = targetParent.toRealPath().normalize();
        Path targetAbs = targetParentReal.resolve(target.getFileName()).normalize();

        // Reject same path or descendant
        if (targetAbs.equals(sourceReal) || targetAbs.startsWith(sourceReal)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot move a directory into itself or its subdirectory");
        }
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

    // =========================
    // Directory listing helpers
    // =========================
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

    private void requireExists(Path p, String cleaned) {
        requireExists(p, cleaned, "Path not found: ");
    }

    private void requireExists(Path p, String cleaned, String messagePrefix) {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, messagePrefix + cleaned);
        }
    }

    private void requireNotExists(Path p, String cleaned, String messagePrefix) {
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, messagePrefix + cleaned);
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

    private void requireReadable(Path p, String cleaned) {
        if (!Files.isReadable(p)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No read permission for file: " + cleaned);
        }
    }

    private void requireWritable(Path p, String message) {
        if (!Files.isWritable(p)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireNotDirectory(Path p, String cleaned) {
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Path is a directory: " + cleaned);
        }
    }

    private void requireDirectory(Path p, String cleaned, String msgPrefix) {
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgPrefix + cleaned);
        }
    }

    private Path requireParentExistsAndDirectory(
            Path requested, String cleaned, String notFoundPrefix) {
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

    private static Path requireParent(Path p, String message) {
        Path parent = p.getParent();
        if (parent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return parent;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private void deleteDirectoryRecursive(Path dir) throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    throw new java.io.UncheckedIOException(e);
                                }
                            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
