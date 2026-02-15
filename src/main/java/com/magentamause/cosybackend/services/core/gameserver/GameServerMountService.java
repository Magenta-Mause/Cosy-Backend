package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.configs.properties.EngineProperties;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.services.core.gameserver.nativefs.CosyFsHandle;
import com.magentamause.cosybackend.services.core.gameserver.nativefs.CosyFsNative;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
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

    private final CosyFsHandle cosyFsHandle;

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

    private boolean nativeAvailable() {
        return cosyFsHandle.available();
    }

    private CosyFsNative nativeLib() {
        return cosyFsHandle.require();
    }

    private RuntimeException mapNativeErr(CosyFsNative.CosyfsError err, String fallbackMsg) {
        String msg = fallbackMsg;
        int code = 0;

        try {
            if (err != null) {
                // defensive: make sure fields are current
                try {
                    err.read();
                } catch (Throwable ignored) {
                }

                code = err.code;
                String m = err.messageString();
                if (m != null && !m.isBlank()) {
                    msg = m;
                }
            }
        } catch (Throwable ignored) {
            // don't let mapping fail
        } finally {
            // free error message if provided
            try {
                if (err != null) {
                    try {
                        err.read();
                    } catch (Throwable ignored) {
                    }
                    if (err.message != null) {
                        nativeLib().cosyfs_free_cstring(err.message);
                        err.message = null;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return switch (code) {
            case 2 -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg); // ENOENT
            case 13 -> new ResponseStatusException(HttpStatus.FORBIDDEN, msg); // EACCES
            case 17 -> new ResponseStatusException(HttpStatus.CONFLICT, msg); // EEXIST
            case 21 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); // EISDIR
            case 22 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); // EINVAL
            case 27 -> new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, msg); // EFBIG
            default -> new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, msg + " (errno=" + code + ")");
        };
    }

    private Optional<byte[]> tryReadFileNative(Path volumeRoot, Path rel, long maxBytes) {
        if (!nativeAvailable()) return Optional.empty();

        String relStr = rel.toString().replace("\\", "/");

        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();
        PointerByReference outPtr = new PointerByReference();
        LongByReference outLen = new LongByReference();

        try {
            // init struct memory
            err.write();

            int rc =
                    nativeLib()
                            .cosyfs_read_file(
                                    volumeRoot.toString(), relStr, maxBytes, outPtr, outLen, err);

            // read back fields set by native
            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native read failed: " + relStr);
            }

            long len = outLen.getValue();
            Pointer p = outPtr.getValue();

            if (len < 0 || len > Integer.MAX_VALUE) {
                if (p != null) {
                    nativeLib().cosyfs_free_buf(p, len);
                }
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "File too large to read into memory");
            }

            byte[] out = (p == null) ? new byte[0] : p.getByteArray(0, (int) len);
            if (p != null) {
                nativeLib().cosyfs_free_buf(p, len);
            }
            return Optional.of(out);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for read (falling back): {}", e.toString());
            return Optional.empty();
        }
    }

    private boolean tryWriteFileNative(Path volumeRoot, Path rel, byte[] content, int mode) {
        if (!nativeAvailable()) return false;

        String relStr = rel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            Memory mem = new Memory(Math.max(content.length, 1));
            if (content.length > 0) {
                mem.write(0, content, 0, content.length);
            }

            err.write();

            int rc =
                    nativeLib()
                            .cosyfs_write_file_truncate(
                                    volumeRoot.toString(), relStr, mem, content.length, mode, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native write failed: " + relStr);
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for write (falling back): {}", e.toString());
            return false;
        }
    }

    private boolean tryRenameNative(Path volumeRoot, Path oldRel, Path newRel) {
        if (!nativeAvailable()) return false;

        String oldStr = oldRel.toString().replace("\\", "/");
        String newStr = newRel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            err.write();

            int rc = nativeLib().cosyfs_rename(volumeRoot.toString(), oldStr, newStr, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native rename failed");
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for rename (falling back): {}", e.toString());
            return false;
        }
    }

    private boolean tryDeleteFileNative(Path volumeRoot, Path rel) {
        if (!nativeAvailable()) return false;

        String relStr = rel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            err.write();

            int rc = nativeLib().cosyfs_delete_file(volumeRoot.toString(), relStr, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native delete failed: " + relStr);
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for delete (falling back): {}", e.toString());
            return false;
        }
    }

    // =========================
    // Public API
    // =========================

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
     * Priority order: 1) Native Rust lib (if available) 2) SecureDirectoryStream ops (best-effort)
     * 3) Path-based TOCTOU-vulnerable fallback
     */
    public byte[] readFileFromBindMountVolume(String serverUuid, String requestedPath) {
        return withReadLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "File path must not be volume root");
                    }

                    Path rel = safeRelativePath(resolved.innerRelative(), "File path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    // (1) Native first
                    long maxFileSize = 128L * 1024L * 1024L;
                    Optional<byte[]> nativeResult = tryReadFileNative(volumeRoot, rel, maxFileSize);
                    if (nativeResult.isPresent()) {
                        return nativeResult.get();
                    }

                    // (2) SecureDirectoryStream next
                    SecureRoot sr = openRootDirectoryStream(volumeRoot);
                    if (sr.secure()) {
                        try (SecureDirectoryStream<Path> root = sr.sds()) {
                            return readFileSecure(root, rel);
                        } catch (AccessDeniedException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Access denied", e);
                        } catch (NoSuchFileException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Path not found: " + rel, e);
                        } catch (IOException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to read file: " + rel,
                                    e);
                        }
                    }

                    // (3) Fallback: not TOCTOU-safe
                    Path requested = volumeRoot.resolve(rel).normalize();
                    requireExists(requested, rel.toString());
                    requireNotDirectory(requested, rel.toString());
                    requireRealPathInsideRoot(volumeRoot, requested, rel.toString());
                    requireReadable(requested, rel.toString());

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
                                HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file: " + rel, e);
                    }
                });
    }

    private byte[] readFileSecure(SecureDirectoryStream<Path> root, Path rel) throws IOException {
        SecureTarget t = resolveSecureNoSymlink(root, rel, "File path");
        try {
            BasicFileAttributes attrs =
                    t.parentDir()
                            .getFileAttributeView(
                                    t.leafName(),
                                    BasicFileAttributeView.class,
                                    LinkOption.NOFOLLOW_LINKS)
                            .readAttributes();

            if (attrs.isSymbolicLink()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Symlinks are not allowed: " + rel);
            }
            if (attrs.isDirectory()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Path is a directory: " + rel);
            }

            try (SeekableByteChannel ch =
                    t.parentDir().newByteChannel(t.leafName(), Set.of(StandardOpenOption.READ))) {
                long max = 128L * 1024L * 1024L;
                long size = ch.size();
                if (size > max) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "File size exceeds maximum allowed size of 128MB");
                }
                if (size > Integer.MAX_VALUE) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE, "File is too large to read into memory");
                }

                byte[] out = new byte[(int) size];
                ByteBuffer buf = ByteBuffer.wrap(out);
                while (buf.hasRemaining()) {
                    int r = ch.read(buf);
                    if (r < 0) break;
                }
                return out;
            }
        } finally {
            closeQuietly(t.toClose());
        }
    }

    public void createDirectoryInBindMountVolume(String serverUuid, String requestedPath) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to create volume root");
                    }

                    Path rel = safeRelativePath(resolved.innerRelative(), "Directory path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    Path requested = volumeRoot.resolve(rel).normalize();

                    Path parent =
                            requireParentExistsAndDirectory(
                                    requested, rel.toString(), "Parent directory does not exist: ");
                    requireRealPathInsideRoot(volumeRoot, parent, rel.toString());

                    requireWritable(parent, "No permission to create directory in: " + parent);
                    requireNotExists(requested, rel.toString(), "Directory already exists: ");

                    try {
                        Files.createDirectory(requested);
                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied while creating directory", e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to create directory: " + rel,
                                e);
                    }
                });
    }

    public void uploadFileToBindMountVolume(
            String serverUuid, String requestedPath, byte[] fileContent) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "File path must not be volume root");
                    }

                    Path rel = safeRelativePath(resolved.innerRelative(), "File path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    // (1) Native first (truncate+write). Mode: choose something reasonable.
                    if (tryWriteFileNative(volumeRoot, rel, fileContent, 0644)) {
                        return;
                    }

                    // (2) SecureDirectoryStream next
                    SecureRoot sr = openRootDirectoryStream(volumeRoot);
                    if (sr.secure()) {
                        try (SecureDirectoryStream<Path> root = sr.sds()) {
                            uploadFileSecure(root, rel, fileContent);
                            return;
                        } catch (AccessDeniedException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Access denied while writing file", e);
                        } catch (NoSuchFileException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Parent directory does not exist: " + rel,
                                    e);
                        } catch (IOException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to write file: " + rel,
                                    e);
                        }
                    }

                    // (3) Fallback (not TOCTOU-safe)
                    Path requested = volumeRoot.resolve(rel).normalize();
                    Path parent =
                            requireParentExistsAndDirectory(
                                    requested, rel.toString(), "Parent directory does not exist: ");
                    requireRealPathInsideRoot(volumeRoot, parent, rel.toString());

                    requireWritable(parent, "No write permission for directory: " + parent);

                    if (Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
                        requireNotDirectory(requested, rel.toString());
                        requireWritable(requested, "No write permission for file: " + rel);
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
                            tempFile = null;
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(tempFile, requested, StandardCopyOption.REPLACE_EXISTING);
                            tempFile = null;
                        }
                    } catch (AccessDeniedException e) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Access denied while writing file", e);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to write file: " + rel,
                                e);
                    } finally {
                        if (tempFile != null) {
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                });
    }

    private void uploadFileSecure(SecureDirectoryStream<Path> root, Path rel, byte[] fileContent)
            throws IOException {

        SecureTarget t = resolveSecureNoSymlink(root, rel, "File path");
        try {
            String base = t.leafName().toString();
            Path tmp = Paths.get(base + ".upload." + java.util.UUID.randomUUID());

            try (SeekableByteChannel ch =
                    t.parentDir()
                            .newByteChannel(
                                    tmp,
                                    Set.of(
                                            StandardOpenOption.CREATE_NEW,
                                            StandardOpenOption.WRITE))) {

                ByteBuffer buf = ByteBuffer.wrap(fileContent);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
            }

            try {
                BasicFileAttributes tgtAttrs =
                        t.parentDir()
                                .getFileAttributeView(
                                        t.leafName(),
                                        BasicFileAttributeView.class,
                                        LinkOption.NOFOLLOW_LINKS)
                                .readAttributes();
                if (tgtAttrs.isSymbolicLink()) {
                    try {
                        t.parentDir().deleteFile(tmp);
                    } catch (IOException ignored) {
                    }
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Refusing to overwrite symlink: " + rel);
                }
                if (tgtAttrs.isDirectory()) {
                    try {
                        t.parentDir().deleteFile(tmp);
                    } catch (IOException ignored) {
                    }
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Target is a directory: " + rel);
                }
            } catch (NoSuchFileException ignored) {
                // ok
            }

            try {
                t.parentDir().move(tmp, t.parentDir(), t.leafName());
            } catch (FileAlreadyExistsException alreadyExists) {
                try {
                    t.parentDir().deleteFile(t.leafName());
                } catch (IOException delEx) {
                    try {
                        t.parentDir().deleteFile(tmp);
                    } catch (IOException ignored) {
                    }
                    throw delEx;
                }
                t.parentDir().move(tmp, t.parentDir(), t.leafName());
            } catch (IOException moveEx) {
                try {
                    t.parentDir().deleteFile(tmp);
                } catch (IOException ignored) {
                }
                throw moveEx;
            }
        } finally {
            closeQuietly(t.toClose());
        }
    }

    public void renameInBindMountVolume(
            String serverUuid, String oldRequestedPath, String newRequestedPath) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount oldResolved = resolveBindMount(serverUuid, oldRequestedPath);
                    ResolvedBindMount newResolved = resolveBindMount(serverUuid, newRequestedPath);

                    if (oldResolved.isRootRequest() || newResolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Renaming volume root is not allowed");
                    }

                    Path oldRel = safeRelativePath(oldResolved.innerRelative(), "oldPath", false);
                    Path newRel = safeRelativePath(newResolved.innerRelative(), "newPath", false);

                    boolean sameMount = oldResolved.volumeUuid().equals(newResolved.volumeUuid());
                    if (!sameMount) {
                        renameInBindMountVolumeFallback(
                                serverUuid, oldRequestedPath, newRequestedPath);
                        return;
                    }

                    Path volumeRoot = oldResolved.volumeRoot();

                    // (1) Native first (same-root rename)
                    if (tryRenameNative(volumeRoot, oldRel, newRel)) {
                        return;
                    }

                    // (2) SecureDirectoryStream next
                    SecureRoot sr = openRootDirectoryStream(volumeRoot);
                    if (sr.secure()) {
                        try (SecureDirectoryStream<Path> root = sr.sds()) {
                            renameSecureSameMount(root, oldRel, newRel);
                            return;
                        } catch (FileAlreadyExistsException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT, "Target already exists: " + newRel, e);
                        } catch (NoSuchFileException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Path not found", e);
                        } catch (AccessDeniedException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Access denied while renaming", e);
                        } catch (IOException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to rename/move", e);
                        }
                    }

                    // (3) Fallback
                    renameInBindMountVolumeFallback(serverUuid, oldRequestedPath, newRequestedPath);
                });
    }

    private void renameInBindMountVolumeFallback(
            String serverUuid, String oldRequestedPath, String newRequestedPath) {

        ResolvedBindMount oldResolved = resolveBindMount(serverUuid, oldRequestedPath);
        ResolvedBindMount newResolved = resolveBindMount(serverUuid, newRequestedPath);

        Path sourceRoot = oldResolved.volumeRoot();
        Path targetRoot = newResolved.volumeRoot();

        Path source = oldResolved.requested();
        Path target = newResolved.requested();

        String oldClean =
                requireNonBlank(
                        cleanRelative(oldResolved.innerRelative()), "oldPath must not be empty");
        String newClean =
                requireNonBlank(
                        cleanRelative(newResolved.innerRelative()), "newPath must not be empty");

        if (oldResolved.isRootRequest() || newResolved.isRootRequest()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Renaming volume root is not allowed");
        }

        requireExists(source, oldClean);

        if (Files.isSymbolicLink(source)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Renaming symlinks is not allowed: " + oldClean);
        }

        Path sourceParent = requireParent(source, "Invalid oldPath");
        Path targetParent = requireParent(target, "Invalid newPath");

        requireExists(targetParent, newClean, "Target parent directory does not exist: ");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found", e);
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
                Files.delete(source);
            }
        } catch (AccessDeniedException e) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied while renaming", e);
        } catch (FileAlreadyExistsException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Target already exists: " + newClean, e);
        } catch (NoSuchFileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found", e);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to rename/move", e);
        }
    }

    private void copyDirectoryRecursiveNoSymlinks(Path sourceDir, Path targetDir)
            throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(sourceDir)) {
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

    private void renameSecureSameMount(SecureDirectoryStream<Path> root, Path oldRel, Path newRel)
            throws IOException {

        SecureTarget src = resolveSecureNoSymlink(root, oldRel, "oldPath");
        SecureTarget dst = resolveSecureNoSymlink(root, newRel, "newPath");
        try {
            if (newRel.startsWith(oldRel)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot move a directory into itself or its subdirectory");
            }
            src.parentDir().move(src.leafName(), dst.parentDir(), dst.leafName());
        } finally {
            closeQuietly(src.toClose());
            closeQuietly(dst.toClose());
        }
    }

    public void deleteInBindMountVolume(String serverUuid, String requestedPath) {
        withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved = resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to delete volume root");
                    }

                    Path rel = safeRelativePath(resolved.innerRelative(), "Path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    // (1) Native first (only handles file unlink in minimal Rust API).
                    // If it succeeds, we're done. If it fails with "is a directory", we'll
                    // continue.
                    try {
                        if (tryDeleteFileNative(volumeRoot, rel)) {
                            return;
                        }
                    } catch (ResponseStatusException rse) {
                        // If native said it's a directory, fall through to Java deletion
                        if (rse.getStatusCode() != HttpStatus.BAD_REQUEST) {
                            throw rse;
                        }
                    }

                    // (2) SecureDirectoryStream next
                    SecureRoot sr = openRootDirectoryStream(volumeRoot);
                    if (sr.secure()) {
                        try (SecureDirectoryStream<Path> root = sr.sds()) {
                            deleteSecure(root, rel);
                            return;
                        } catch (AccessDeniedException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.FORBIDDEN,
                                    "Access denied while deleting: " + rel,
                                    e);
                        } catch (NoSuchFileException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Path not found: " + rel, e);
                        } catch (IOException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to delete: " + rel,
                                    e);
                        }
                    }

                    // (3) Fallback (not TOCTOU-safe)
                    Path requested = volumeRoot.resolve(rel).normalize();
                    if (requested.equals(volumeRoot)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to delete volume root");
                    }

                    requireExists(requested, rel.toString());
                    if (Files.isSymbolicLink(requested)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Deleting symlinks is not allowed: " + rel);
                    }

                    Path parent = requireParent(requested, "Invalid path: " + rel);
                    requireRealPathInsideRoot(volumeRoot, requested, rel.toString());
                    requireRealPathInsideRoot(volumeRoot, parent, rel.toString());
                    requireWritable(parent, "No permission to delete from: " + parent);

                    try {
                        if (Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
                            deleteDirectoryRecursive(requested);
                        } else {
                            Files.delete(requested);
                        }
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete: " + rel, e);
                    }
                });
    }

    private void deleteSecure(SecureDirectoryStream<Path> root, Path rel) throws IOException {
        SecureTarget t = resolveSecureNoSymlink(root, rel, "Path");
        try {
            BasicFileAttributes attrs =
                    t.parentDir()
                            .getFileAttributeView(
                                    t.leafName(),
                                    BasicFileAttributeView.class,
                                    LinkOption.NOFOLLOW_LINKS)
                            .readAttributes();

            if (attrs.isSymbolicLink()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Deleting symlinks is not allowed: " + rel);
            }

            if (attrs.isDirectory()) {
                deleteDirRecursiveSecure(t.parentDir(), t.leafName());
            } else {
                t.parentDir().deleteFile(t.leafName());
            }
        } finally {
            closeQuietly(t.toClose());
        }
    }

    private void deleteDirRecursiveSecure(SecureDirectoryStream<Path> parentDir, Path dirName)
            throws IOException {
        try (DirectoryStream<Path> ds =
                parentDir.newDirectoryStream(dirName, LinkOption.NOFOLLOW_LINKS)) {
            if (!(ds instanceof SecureDirectoryStream<Path> sds)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "SecureDirectoryStream lost while deleting");
            }

            for (Path child : ds) {
                Path name = child.getFileName();
                if (name == null) continue;

                BasicFileAttributes attrs =
                        sds.getFileAttributeView(
                                        name,
                                        BasicFileAttributeView.class,
                                        LinkOption.NOFOLLOW_LINKS)
                                .readAttributes();

                if (attrs.isSymbolicLink()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Symlinks are not allowed in delete tree: " + name);
                }

                if (attrs.isDirectory()) {
                    deleteDirRecursiveSecure(sds, name);
                } else {
                    sds.deleteFile(name);
                }
            }
        }

        parentDir.deleteDirectory(dirName);
    }

    private ResolvedBindMount resolveBindMount(String serverUuid, String requestedPath) {
        GameServerEntity server = gameServerService.getGameServerById(serverUuid);

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

    private static void requireTargetNotInsideSourceDir(Path source, Path target)
            throws IOException {
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

    private record SecureRoot(SecureDirectoryStream<Path> sds, boolean secure) {}

    private SecureRoot openRootDirectoryStream(Path volumeRoot) {
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

    private static Path safeRelativePath(String input, String label, boolean allowEmpty) {
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

    private record SecureTarget(
            SecureDirectoryStream<Path> parentDir,
            Path leafName,
            List<DirectoryStream<Path>> toClose) {}

    private SecureTarget resolveSecureNoSymlink(
            SecureDirectoryStream<Path> root, Path rel, String label) throws IOException {

        int n = rel.getNameCount();
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must not be empty");
        }

        List<DirectoryStream<Path>> opened = new ArrayList<>();
        SecureDirectoryStream<Path> cur = root;

        for (int i = 0; i < n - 1; i++) {
            Path part = rel.getName(i);

            BasicFileAttributes attrs =
                    cur.getFileAttributeView(
                                    part, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                            .readAttributes();

            if (!attrs.isDirectory()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Parent component is not a directory: " + part);
            }

            DirectoryStream<Path> next = cur.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
            opened.add(next);

            if (!(next instanceof SecureDirectoryStream<Path> nextSecure)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "SecureDirectoryStream lost while resolving");
            }
            cur = nextSecure;
        }

        return new SecureTarget(cur, rel.getFileName(), opened);
    }

    private void closeQuietly(List<DirectoryStream<Path>> streams) {
        for (int i = streams.size() - 1; i >= 0; i--) {
            try {
                streams.get(i).close();
            } catch (IOException ignored) {
            }
        }
    }
}
