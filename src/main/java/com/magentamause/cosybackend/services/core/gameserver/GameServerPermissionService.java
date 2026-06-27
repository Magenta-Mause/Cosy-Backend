package com.magentamause.cosybackend.services.core.gameserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
                    Path target = resolved.requested();
                    if (!resolved.isRootRequest()) {
                        resolver.requireRealPathInsideRoot(
                                resolved.volumeRoot(), target, resolved.innerRelative());
                    }
                    try {
                        Files.setPosixFilePermissions(target, fromUnixMode(mode & 0777));
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to set permissions on " + requestedPath,
                                e);
                    }
                    if (uid != null) {
                        try {
                            Files.setAttribute(
                                    target, "unix:uid", uid, LinkOption.NOFOLLOW_LINKS);
                            Files.setAttribute(
                                    target, "unix:gid", uid, LinkOption.NOFOLLOW_LINKS);
                        } catch (UnsupportedOperationException | IOException e) {
                            log.warn(
                                    "Could not chown {} to uid {}: {}",
                                    target,
                                    uid,
                                    e.getMessage());
                        }
                    }
                });
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
