package com.magentamause.cosybackend.services.core.gameserver;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
class GameServerDirectoryService {

    private final GameServerMountLocks locks;
    private final GameServerMountResolver resolver;
    private final GameServerNativeOps nativeOps;

    void createDirectoryInBindMountVolume(String serverUuid, String requestedPath) {
        locks.withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to create volume root");
                    }

                    Path rel =
                            GameServerMountResolver.safeRelativePath(
                                    resolved.innerRelative(), "Directory path", false);
                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = volumeRoot.resolve(rel).normalize();

                    Path parent =
                            resolver.requireParentExistsAndDirectory(
                                    requested, rel.toString(), "Parent directory does not exist: ");
                    resolver.requireRealPathInsideRoot(volumeRoot, parent, rel.toString());
                    resolver.requireWritable(
                            parent, "No permission to create directory in: " + parent);
                    resolver.requireNotExists(
                            requested, rel.toString(), "Directory already exists: ");

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

    void renameInBindMountVolume(
            String serverUuid, String oldRequestedPath, String newRequestedPath) {
        locks.withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount oldResolved =
                            resolver.resolveBindMount(serverUuid, oldRequestedPath);
                    ResolvedBindMount newResolved =
                            resolver.resolveBindMount(serverUuid, newRequestedPath);

                    if (oldResolved.isRootRequest() || newResolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Renaming volume root is not allowed");
                    }

                    Path oldRel =
                            GameServerMountResolver.safeRelativePath(
                                    oldResolved.innerRelative(), "oldPath", false);
                    Path newRel =
                            GameServerMountResolver.safeRelativePath(
                                    newResolved.innerRelative(), "newPath", false);

                    boolean sameMount = oldResolved.volumeUuid().equals(newResolved.volumeUuid());
                    if (!sameMount) {
                        renameInBindMountVolumeFallback(
                                serverUuid, oldRequestedPath, newRequestedPath);
                        return;
                    }

                    Path volumeRoot = oldResolved.volumeRoot();

                    if (nativeOps.tryRenameNative(volumeRoot, oldRel, newRel)) {
                        return;
                    }

                    SecureRoot sr = resolver.openRootDirectoryStream(volumeRoot);
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

                    renameInBindMountVolumeFallback(serverUuid, oldRequestedPath, newRequestedPath);
                });
    }

    private void renameInBindMountVolumeFallback(
            String serverUuid, String oldRequestedPath, String newRequestedPath) {

        ResolvedBindMount oldResolved = resolver.resolveBindMount(serverUuid, oldRequestedPath);
        ResolvedBindMount newResolved = resolver.resolveBindMount(serverUuid, newRequestedPath);

        Path sourceRoot = oldResolved.volumeRoot();
        Path targetRoot = newResolved.volumeRoot();
        Path source = oldResolved.requested();
        Path target = newResolved.requested();

        String oldClean =
                GameServerMountResolver.requireNonBlank(
                        resolver.cleanRelative(oldResolved.innerRelative()),
                        "oldPath must not be empty");
        String newClean =
                GameServerMountResolver.requireNonBlank(
                        resolver.cleanRelative(newResolved.innerRelative()),
                        "newPath must not be empty");

        if (oldResolved.isRootRequest() || newResolved.isRootRequest()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Renaming volume root is not allowed");
        }

        resolver.requireExists(source, oldClean);

        if (Files.isSymbolicLink(source)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Renaming symlinks is not allowed: " + oldClean);
        }

        Path sourceParent = GameServerMountResolver.requireParent(source, "Invalid oldPath");
        Path targetParent = GameServerMountResolver.requireParent(target, "Invalid newPath");

        resolver.requireExists(targetParent, newClean, "Target parent directory does not exist: ");
        resolver.requireDirectory(targetParent, newClean, "Target parent is not a directory: ");
        resolver.requireNotExists(target, newClean, "Target already exists: ");

        resolver.requireRealPathInsideRoot(sourceRoot, source, oldClean);
        resolver.requireRealPathInsideRoot(sourceRoot, sourceParent, oldClean);
        resolver.requireRealPathInsideRoot(targetRoot, targetParent, newClean);

        try {
            GameServerMountResolver.requireTargetNotInsideSourceDir(source, target);
        } catch (AccessDeniedException e) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied while validating rename", e);
        } catch (NoSuchFileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found", e);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to validate rename", e);
        }

        resolver.requireReadable(source, oldClean);
        resolver.requireWritable(sourceParent, "No permission to modify: " + sourceParent);
        resolver.requireWritable(targetParent, "No permission to write into: " + targetParent);

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

    private void renameSecureSameMount(
            SecureDirectoryStream<Path> root, Path oldRel, Path newRel) throws IOException {

        SecureTarget src = resolver.resolveSecureNoSymlink(root, oldRel, "oldPath");
        SecureTarget dst = resolver.resolveSecureNoSymlink(root, newRel, "newPath");

        try {
            Path oldN = oldRel.normalize();
            Path newN = newRel.normalize();

            if (newN.equals(oldN) || newN.startsWith(oldN)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot move a directory into itself or its subdirectory");
            }

            src.parentDir().move(src.leafName(), dst.parentDir(), dst.leafName());
        } finally {
            resolver.closeQuietly(src.toClose());
            resolver.closeQuietly(dst.toClose());
        }
    }

    void deleteInBindMountVolume(String serverUuid, String requestedPath) {
        locks.withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);

                    if (resolved.isRootRequest()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to delete volume root");
                    }

                    Path rel =
                            GameServerMountResolver.safeRelativePath(
                                    resolved.innerRelative(), "Path", false);
                    Path volumeRoot = resolved.volumeRoot();

                    try {
                        if (nativeOps.tryDeleteFileNative(volumeRoot, rel)) {
                            return;
                        }
                    } catch (ResponseStatusException rse) {
                        if (rse.getStatusCode() != HttpStatus.BAD_REQUEST) {
                            throw rse;
                        }
                    }

                    SecureRoot sr = resolver.openRootDirectoryStream(volumeRoot);
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

                    Path requested = volumeRoot.resolve(rel).normalize();
                    if (requested.equals(volumeRoot)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refusing to delete volume root");
                    }

                    resolver.requireExists(requested, rel.toString());
                    if (Files.isSymbolicLink(requested)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Deleting symlinks is not allowed: " + rel);
                    }

                    Path parent =
                            GameServerMountResolver.requireParent(requested, "Invalid path: " + rel);
                    resolver.requireRealPathInsideRoot(volumeRoot, requested, rel.toString());
                    resolver.requireRealPathInsideRoot(volumeRoot, parent, rel.toString());
                    resolver.requireWritable(parent, "No permission to delete from: " + parent);

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
        SecureTarget t = resolver.resolveSecureNoSymlink(root, rel, "Path");
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
            resolver.closeQuietly(t.toClose());
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

    void deleteDirectoryRecursive(Path dir) throws IOException {
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
