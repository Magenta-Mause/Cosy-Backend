package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.DirectorySizeDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
class GameServerFileListingService {

    private final GameServerMountLocks locks;
    private final GameServerMountResolver resolver;

    GameServerFileSystemDto readBindMountFileSystem(
            String serverUuid, String requestedPath, int fetchDepth) {
        return locks.withReadLock(
                serverUuid,
                () -> {
                    if (fetchDepth < 0) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "fetchDepth must be >= 0");
                    }

                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);
                    Path volumeRoot = resolved.volumeRoot();
                    Path requested = resolved.requested();

                    if (!resolved.isRootRequest()) {
                        resolver.requireExists(requested, resolved.innerRelative());
                        resolver.requireRealPathInsideRoot(
                                volumeRoot, requested, resolved.innerRelative());
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

    DirectorySizeDto getDirectorySize(String serverUuid, String requestedPath) {
        return locks.withReadLock(
                serverUuid,
                () -> {
                    ResolvedBindMount resolved =
                            resolver.resolveBindMount(serverUuid, requestedPath);
                    Path startPath = resolved.requested();

                    if (!resolved.isRootRequest()) {
                        resolver.requireExists(startPath, resolved.innerRelative());
                        resolver.requireRealPathInsideRoot(
                                resolved.volumeRoot(), startPath, resolved.innerRelative());
                    }

                    if (!Files.isDirectory(startPath, LinkOption.NOFOLLOW_LINKS)) {
                        try {
                            return new DirectorySizeDto(Files.size(startPath));
                        } catch (IOException e) {
                            return new DirectorySizeDto(0L);
                        }
                    }

                    long[] total = {0L};
                    try (java.util.stream.Stream<Path> walk = Files.walk(startPath)) {
                        walk.sorted()
                                .forEach(
                                        entry -> {
                                            try {
                                                BasicFileAttributes attrs =
                                                        Files.readAttributes(
                                                                entry,
                                                                BasicFileAttributes.class,
                                                                LinkOption.NOFOLLOW_LINKS);
                                                if (attrs.isRegularFile()) {
                                                    total[0] += attrs.size();
                                                }
                                            } catch (NoSuchFileException e) {
                                                // skip deleted
                                            } catch (IOException e) {
                                                log.debug(
                                                        "Could not read size of {}: {}",
                                                        entry,
                                                        e.getMessage());
                                            }
                                        });
                    } catch (IOException e) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to calculate directory size",
                                e);
                    }

                    return new DirectorySizeDto(total[0]);
                });
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

    List<GameServerFileSystemDto.FileSystemObjectDto> listDirectory(Path dir, int fetchDepth) {
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

    GameServerFileSystemDto.FileSystemObjectDto toNode(Path p, int fetchDepth) {
        boolean isDir = Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS);
        Optional<Integer> perms = tryReadPermissions(p);
        Optional<Integer> uid = tryReadUid(p);
        Optional<Long> size = tryReadSize(p);

        return GameServerFileSystemDto.FileSystemObjectDto.builder()
                .fetchDepth(fetchDepth)
                .name(p.getFileName() != null ? p.getFileName().toString() : p.toString())
                .type(
                        isDir
                                ? GameServerFileSystemDto.FileType.DIRECTORY
                                : GameServerFileSystemDto.FileType.FILE)
                .permissions(perms)
                .uid(uid)
                .size(size)
                .children(new ArrayList<>())
                .build();
    }

    private Optional<Integer> tryReadUid(Path p) {
        try {
            Object val = Files.getAttribute(p, "unix:uid", LinkOption.NOFOLLOW_LINKS);
            if (val instanceof Integer i) return Optional.of(i);
            return Optional.empty();
        } catch (UnsupportedOperationException | IOException ignored) {
            return Optional.empty();
        }
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
}
