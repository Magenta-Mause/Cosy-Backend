package com.magentamause.cosybackend.services.core.gameserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
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
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
class GameServerFileIoService {

    private final GameServerMountLocks locks;
    private final GameServerMountResolver resolver;
    private final GameServerNativeOps nativeOps;

    byte[] readFileFromBindMountVolume(String serverUuid, String requestedPath) {
        return locks.withReadLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "File path must not be volume root");
                    }

                    Path rel =
                            GameServerMountResolver.safeRelativePath(
                                    resolved.innerRelative(), "File path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    long maxFileSize = 128L * 1024L * 1024L;
                    Optional<byte[]> nativeResult =
                            nativeOps.tryReadFileNative(volumeRoot, rel, maxFileSize);
                    if (nativeResult.isPresent()) {
                        return nativeResult.get();
                    }

                    SecureRoot sr = resolver.openRootDirectoryStream(volumeRoot);
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

                    Path requested = volumeRoot.resolve(rel).normalize();
                    resolver.requireExists(requested, rel.toString());
                    resolver.requireNotDirectory(requested, rel.toString());
                    resolver.requireRealPathInsideRoot(volumeRoot, requested, rel.toString());
                    resolver.requireReadable(requested, rel.toString());

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
        SecureTarget t = resolver.resolveSecureNoSymlink(root, rel, "File path");
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
            resolver.closeQuietly(t.toClose());
        }
    }

    void uploadFileToBindMountVolume(String serverUuid, String requestedPath, byte[] fileContent) {
        locks.withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "File path must not be volume root");
                    }

                    Path rel =
                            GameServerMountResolver.safeRelativePath(
                                    resolved.innerRelative(), "File path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    if (nativeOps.tryWriteFileNative(volumeRoot, rel, fileContent, 0644)) {
                        return;
                    }

                    SecureRoot sr = resolver.openRootDirectoryStream(volumeRoot);
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

                    Path requested = volumeRoot.resolve(rel).normalize();
                    Path parent =
                            resolver.requireParentExistsAndDirectory(
                                    requested, rel.toString(), "Parent directory does not exist: ");
                    resolver.requireRealPathInsideRoot(volumeRoot, parent, rel.toString());
                    resolver.requireWritable(
                            parent, "No write permission for directory: " + parent);

                    if (Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
                        resolver.requireNotDirectory(requested, rel.toString());
                        resolver.requireWritable(requested, "No write permission for file: " + rel);
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

        SecureTarget t = resolver.resolveSecureNoSymlink(root, rel, "File path");
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
                // ok - target doesn't exist yet
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
            resolver.closeQuietly(t.toClose());
        }
    }
}
