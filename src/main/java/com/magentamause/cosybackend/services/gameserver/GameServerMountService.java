package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import com.magentamause.cosybackend.repositories.GameServerRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerMountService {

    private final GameServerRepository gameServerRepository;

    public GameServerFileSystemDto readBindMountFileSystem(
            String serverUuid, String volumeUuid, String subPath, int fetchDepth) {

        if (fetchDepth < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fetchDepth must be >= 0");
        }

        Path volumeRoot = requireVolumeRoot(serverUuid, volumeUuid);

        boolean isRootRequest = (subPath == null || subPath.isBlank() || "/".equals(subPath));

        Path requested =
                isRootRequest ? volumeRoot : resolveInsideRoot(volumeRoot, subPath, false, "Path");

        if (!isRootRequest) {
            requireExists(requested, subPath);
            requireRealPathInsideRoot(volumeRoot, requested, subPath);
        }

        if (Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
            List<GameServerFileSystemDto.FileSystemObjectDto> entries =
                    listDirectory(requested, fetchDepth);

            return GameServerFileSystemDto.builder()
                    .volumeUuid(volumeUuid)
                    .objects(entries)
                    .build();
        }

        GameServerFileSystemDto.FileSystemObjectDto fileNode = toNode(requested, 0);

        return GameServerFileSystemDto.builder()
                .volumeUuid(volumeUuid)
                .objects(List.of(fileNode))
                .build();
    }

    public byte[] readFileFromBindMountVolume(
            String serverUuid, String volumeUuid, String filePath) {

        Path volumeRoot = requireVolumeRoot(serverUuid, volumeUuid);

        String cleaned = requireNonBlank(cleanRelative(filePath), "File path must not be empty");
        Path requested = resolveInsideRoot(volumeRoot, cleaned, true, "Path");

        requireExists(requested, cleaned);
        requireNotDirectory(requested, cleaned);
        requireRealPathInsideRoot(volumeRoot, requested, cleaned);
        requireReadable(requested, cleaned);

        long maxFileSize = 128 * 1024 * 1024; // 128 MB
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
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file: " + cleaned, e);
        }
    }

    private Path requireVolumeRoot(String serverUuid, String volumeUuid) {
        GameServerEntity server = getGameServerById(serverUuid);
        VolumeMountConfiguration mount = findMount(server, serverUuid, volumeUuid);

        String hostPath = mount.getHostPath();
        if (hostPath == null || hostPath.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Volume mount " + volumeUuid + " does not have a readable host path");
        }

        Path volumeRoot = Paths.get(hostPath).toAbsolutePath().normalize();
        if (!Files.exists(volumeRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Mount path does not exist: " + volumeRoot);
        }
        return volumeRoot;
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

    public GameServerEntity getGameServerById(String uuid) {
        return gameServerRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Game server with uuid " + uuid + " not found"));
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: " + root, ade);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error reading " + root, e);
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
        if (fetchDepth < 0) fetchDepth = 0;

        List<GameServerFileSystemDto.FileSystemObjectDto> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                children.add(buildTree(child, Math.max(fetchDepth - 1, 0)));
            }
        } catch (AccessDeniedException ade) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: " + dir, ade);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error reading " + dir, e);
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

    private VolumeMountConfiguration findMount(
            GameServerEntity server, String serverUuid, String volumeUuid) {
        return Optional.ofNullable(server.getVolumeMounts()).orElse(List.of()).stream()
                .filter(vm -> volumeUuid.equals(vm.getUuid()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No volume mount with uuid "
                                                + volumeUuid
                                                + " found on server "
                                                + serverUuid));
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
        Integer perms = tryReadPermissions(p);

        return GameServerFileSystemDto.FileSystemObjectDto.builder()
                .fetchDepth(fetchDepth)
                .name(p.getFileName() != null ? p.getFileName().toString() : p.toString())
                .type(
                        isDir
                                ? GameServerFileSystemDto.FileType.DIRECTORY
                                : GameServerFileSystemDto.FileType.FILE)
                .permissions(perms)
                .children(new ArrayList<>())
                .build();
    }

    /** Returns a Unix-like permission bitmask (e.g. 0755 -> 493 decimal) when supported. */
    private Integer tryReadPermissions(Path p) {
        try {
            PosixFileAttributes attrs =
                    Files.readAttributes(p, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> perms = attrs.permissions();
            return toUnixMode(perms);
        } catch (UnsupportedOperationException ignored) {
            return null;
        } catch (IOException ignored) {
            return null;
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
}
