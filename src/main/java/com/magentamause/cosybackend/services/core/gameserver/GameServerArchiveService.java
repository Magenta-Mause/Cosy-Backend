package com.magentamause.cosybackend.services.core.gameserver;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
class GameServerArchiveService {

    private final GameServerMountLocks locks;
    private final GameServerMountResolver resolver;
    private final GameServerNativeOps nativeOps;

    void streamDirectoryAsZip(
            String serverUuid, String requestedPath, OutputStream outputStream) throws IOException {
        // Resolve and validate under the read lock, then release before doing any I/O.
        // Holding the lock across the transfer would block writes for the full download duration.
        final Path volumeRoot;
        final Path startPath;

        Lock l = locks.lockForServer(serverUuid).readLock();
        l.lock();
        try {
            ResolvedBindMount resolved = resolver.resolveBindMount(serverUuid, requestedPath);
            volumeRoot = resolved.volumeRoot();
            startPath = resolved.requested();

            if (!resolved.isRootRequest()) {
                resolver.requireExists(startPath, resolved.innerRelative());
                resolver.requireRealPathInsideRoot(
                        volumeRoot, startPath, resolved.innerRelative());
            }
        } finally {
            l.unlock();
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
            if (!Files.isDirectory(startPath, LinkOption.NOFOLLOW_LINKS)) {
                String name =
                        startPath.getFileName() != null
                                ? startPath.getFileName().toString()
                                : "file";
                addFileToZip(zos, startPath, name);
            } else {
                try (java.util.stream.Stream<Path> walk = Files.walk(startPath)) {
                    for (Path entry : (Iterable<Path>) walk::iterator) {
                        BasicFileAttributes attrs;
                        try {
                            attrs =
                                    Files.readAttributes(
                                            entry,
                                            BasicFileAttributes.class,
                                            LinkOption.NOFOLLOW_LINKS);
                        } catch (NoSuchFileException e) {
                            log.debug("Skipping deleted entry in zip: {}", entry);
                            continue;
                        }

                        if (attrs.isSymbolicLink()) {
                            log.debug("Skipping symlink in zip: {}", entry);
                            continue;
                        }
                        if (attrs.isDirectory()) {
                            continue;
                        }

                        Path normalized = entry.normalize();
                        if (!normalized.startsWith(volumeRoot)) {
                            log.warn("Skipping path outside volume root in zip: {}", entry);
                            continue;
                        }

                        String entryName =
                                startPath.relativize(normalized).toString().replace("\\", "/");
                        try {
                            addFileToZip(zos, entry, entryName);
                        } catch (NoSuchFileException e) {
                            log.debug("Skipping deleted file in zip: {}", entry);
                        }
                    }
                }
            }
        }
    }

    void extractArchiveToBindMount(
            String serverUuid, String requestedPath, byte[] zipBytes, boolean clearFirst) {
        locks.withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);
                    Path volumeRoot = resolved.volumeRoot();
                    Path targetDir = resolved.requested();

                    try {
                        Files.createDirectories(targetDir);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to create extraction target directory",
                                e);
                    }

                    if (!resolved.isRootRequest()) {
                        resolver.requireRealPathInsideRoot(
                                volumeRoot, targetDir, resolved.innerRelative());
                    }

                    if (clearFirst && Files.isDirectory(targetDir)) {
                        try (java.util.stream.Stream<Path> walk = Files.walk(targetDir)) {
                            walk.sorted(Comparator.reverseOrder())
                                    .filter(p -> !p.equals(targetDir))
                                    .forEach(
                                            p -> {
                                                try {
                                                    Files.delete(p);
                                                } catch (IOException e) {
                                                    log.warn(
                                                            "Could not delete {} during clear: {}",
                                                            p,
                                                            e.getMessage());
                                                }
                                            });
                        } catch (IOException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to clear target directory",
                                    e);
                        }
                    }

                    try (ZipInputStream zis =
                            new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                        ZipEntry entry;
                        int extracted = 0;

                        while ((entry = zis.getNextEntry()) != null) {
                            try {
                                String entryName = entry.getName().replace("\\", "/");

                                Path entryPath = targetDir.resolve(entryName).normalize();
                                if (!entryPath.startsWith(targetDir.normalize())
                                        || !entryPath.startsWith(volumeRoot)) {
                                    log.warn("Skipping unsafe zip entry: {}", entryName);
                                    continue;
                                }

                                if (entry.isDirectory()) {
                                    Files.createDirectories(entryPath);
                                    continue;
                                }

                                Path parent = entryPath.getParent();
                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }

                                byte[] content = zis.readAllBytes();
                                Path relToRoot = volumeRoot.relativize(entryPath);

                                if (nativeOps.tryWriteFileNative(
                                        volumeRoot, relToRoot, content, 0664)) {
                                    applyOwnership(entryPath);
                                    extracted++;
                                    continue;
                                }

                                Path tempFile =
                                        Files.createTempFile(
                                                parent,
                                                entryPath.getFileName().toString(),
                                                ".extract");
                                try {
                                    Files.write(tempFile, content);
                                    try {
                                        Files.move(
                                                tempFile,
                                                entryPath,
                                                StandardCopyOption.REPLACE_EXISTING,
                                                StandardCopyOption.ATOMIC_MOVE);
                                        tempFile = null;
                                    } catch (AtomicMoveNotSupportedException e) {
                                        Files.move(
                                                tempFile,
                                                entryPath,
                                                StandardCopyOption.REPLACE_EXISTING);
                                        tempFile = null;
                                    }
                                } finally {
                                    if (tempFile != null) {
                                        try {
                                            Files.deleteIfExists(tempFile);
                                        } catch (IOException ignored) {
                                        }
                                    }
                                }

                                try {
                                    Files.setPosixFilePermissions(
                                            entryPath,
                                            PosixFilePermissions.fromString("rw-rw-r--"));
                                } catch (UnsupportedOperationException | IOException e) {
                                    log.debug(
                                            "Could not set permissions on {}: {}",
                                            entryPath,
                                            e.getMessage());
                                }
                                applyOwnership(entryPath);
                                extracted++;

                            } catch (IOException e) {
                                log.warn(
                                        "Failed to extract entry {}: {}",
                                        entry.getName(),
                                        e.getMessage());
                            } finally {
                                zis.closeEntry();
                            }
                        }

                        log.info(
                                "Extracted {} files from archive to {}", extracted, requestedPath);
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Failed to read zip archive: " + e.getMessage(),
                                e);
                    }
                });
    }

    String buildZipArchiveName(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.isBlank()) return "archive";
        return name;
    }

    private void applyOwnership(Path path) {
        try {
            Files.setAttribute(path, "unix:uid", 1000);
            Files.setAttribute(path, "unix:gid", 1000);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Could not chown {} to 1000:1000: {}", path, e.getMessage());
        }
    }

    private void addFileToZip(ZipOutputStream zos, Path file, String entryName)
            throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try {
            Files.copy(file, zos);
        } catch (IOException e) {
            try {
                zos.closeEntry();
            } catch (IOException ignored) {
            }
            throw e;
        }
        zos.closeEntry();
    }
}
