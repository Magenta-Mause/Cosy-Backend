package com.magentamause.cosybackend.services.core.gameserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
class GameServerPermissionService {

    private final GameServerMountLocks locks;
    private final GameServerMountResolver resolver;

    void setFilePermissions(String serverUuid, String requestedPath, int mode, Integer uid) {
        locks.withWriteLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);
                    Path volumeRoot = resolved.volumeRoot();
                    Set<PosixFilePermission> perms = fromUnixMode(mode & 0777);

                    // The volume root itself is validated by the resolver as a real (non-symlink)
                    // directory that lives under the configured base dir, so it is safe to operate
                    // on directly.
                    if (resolved.isRootRequest()) {
                        setPermissionsDirect(volumeRoot, perms, requestedPath);
                        applyOwnership(volumeRoot, uid);
                        return;
                    }

                    Path rel =
                            GameServerMountResolver.safeRelativePath(
                                    resolved.innerRelative(), "Path", false);

                    // Preferred path: resolve through the pinned SecureDirectoryStream fds so that
                    // neither an intermediate component nor the leaf can be a symlink pointing
                    // outside the volume root. Files.setPosixFilePermissions() follows symlinks and
                    // must never be handed an attacker-controlled path.
                    SecureRoot sr = resolver.openRootDirectoryStream(volumeRoot);
                    if (sr.secure()) {
                        try (SecureDirectoryStream<Path> root = sr.sds()) {
                            setPermissionsSecure(root, rel, perms);
                        } catch (IOException e) {
                            throw new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to set permissions on " + requestedPath,
                                    e);
                        }
                        // The secure resolution above verified the whole chain is symlink-free;
                        // chown the leaf with NOFOLLOW so it cannot be redirected either.
                        applyOwnership(volumeRoot.resolve(rel).normalize(), uid);
                        return;
                    }

                    // Fallback for platforms without SecureDirectoryStream support (not Linux):
                    // explicitly reject a symlinked leaf and verify the real path stays inside
                    // root.
                    Path target = volumeRoot.resolve(rel).normalize();
                    if (Files.isSymbolicLink(target)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Symlinks are not allowed: " + rel);
                    }
                    resolver.requireExists(target, rel.toString());
                    resolver.requireRealPathInsideRoot(volumeRoot, target, rel.toString());
                    setPermissionsDirect(target, perms, requestedPath);
                    applyOwnership(target, uid);
                });
    }

    private void setPermissionsSecure(
            SecureDirectoryStream<Path> root, Path rel, Set<PosixFilePermission> perms)
            throws IOException {
        SecureTarget t = resolver.resolveSecureNoSymlink(root, rel, "Path");
        try {
            // Both views are bound to the parent directory fd and NOFOLLOW, so they cannot
            // traverse a symlink. getFileAttributeView may return null if the view type is
            // unavailable on the filesystem.
            BasicFileAttributeView basicView =
                    t.parentDir()
                            .getFileAttributeView(
                                    t.leafName(),
                                    BasicFileAttributeView.class,
                                    LinkOption.NOFOLLOW_LINKS);
            PosixFileAttributeView posixView =
                    t.parentDir()
                            .getFileAttributeView(
                                    t.leafName(),
                                    PosixFileAttributeView.class,
                                    LinkOption.NOFOLLOW_LINKS);
            if (basicView == null || posixView == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "POSIX attribute views are not available for " + rel);
            }

            if (basicView.readAttributes().isSymbolicLink()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Symlinks are not allowed: " + rel);
            }

            try {
                posixView.setPermissions(perms);
            } catch (UnsupportedOperationException e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "POSIX permissions are not supported for " + rel,
                        e);
            }
        } finally {
            resolver.closeQuietly(t.toClose());
        }
    }

    private void setPermissionsDirect(
            Path target, Set<PosixFilePermission> perms, String requestedPath) {
        try {
            Files.setPosixFilePermissions(target, perms);
        } catch (UnsupportedOperationException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "POSIX permissions are not supported on this filesystem",
                    e);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to set permissions on " + requestedPath,
                    e);
        }
    }

    private void applyOwnership(Path target, Integer uid) {
        if (uid == null) {
            return;
        }
        try {
            Files.setAttribute(target, "unix:uid", uid, LinkOption.NOFOLLOW_LINKS);
            Files.setAttribute(target, "unix:gid", uid, LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException | IOException e) {
            log.warn("Could not chown {} to uid {}: {}", target, uid, e.getMessage());
        }
    }

    private Set<PosixFilePermission> fromUnixMode(int mode) {
        Set<PosixFilePermission> p = new HashSet<>();
        if ((mode & 0400) != 0) p.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) p.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) p.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) p.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) p.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) p.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) p.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) p.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) p.add(PosixFilePermission.OTHERS_EXECUTE);
        return p;
    }
}
