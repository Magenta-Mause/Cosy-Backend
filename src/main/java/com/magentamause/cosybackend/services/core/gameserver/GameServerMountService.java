package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.DirectorySizeDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import java.io.IOException;
import java.io.OutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameServerMountService {

    private final GameServerFileListingService listing;
    private final GameServerFileIoService fileIo;
    private final GameServerDirectoryService directory;
    private final GameServerArchiveService archive;
    private final GameServerPermissionService permission;

    public GameServerFileSystemDto readBindMountFileSystem(
            String serverUuid, String requestedPath, int fetchDepth) {
        return listing.readBindMountFileSystem(serverUuid, requestedPath, fetchDepth);
    }

    public byte[] readFileFromBindMountVolume(String serverUuid, String requestedPath) {
        return fileIo.readFileFromBindMountVolume(serverUuid, requestedPath);
    }

    public void uploadFileToBindMountVolume(
            String serverUuid, String requestedPath, byte[] fileContent) {
        fileIo.uploadFileToBindMountVolume(serverUuid, requestedPath, fileContent);
    }

    public void createDirectoryInBindMountVolume(String serverUuid, String requestedPath) {
        directory.createDirectoryInBindMountVolume(serverUuid, requestedPath);
    }

    public void renameInBindMountVolume(
            String serverUuid, String oldRequestedPath, String newRequestedPath) {
        directory.renameInBindMountVolume(serverUuid, oldRequestedPath, newRequestedPath);
    }

    public void deleteInBindMountVolume(String serverUuid, String requestedPath) {
        directory.deleteInBindMountVolume(serverUuid, requestedPath);
    }

    public void streamDirectoryAsZip(
            String serverUuid, String requestedPath, OutputStream outputStream) throws IOException {
        archive.streamDirectoryAsZip(serverUuid, requestedPath, outputStream);
    }

    public void extractArchiveToBindMount(
            String serverUuid, String requestedPath, byte[] zipBytes, boolean clearFirst) {
        archive.extractArchiveToBindMount(serverUuid, requestedPath, zipBytes, clearFirst);
    }

    public void setFilePermissions(String serverUuid, String requestedPath, int mode, Integer uid) {
        permission.setFilePermissions(serverUuid, requestedPath, mode, uid);
    }

    public DirectorySizeDto getDirectorySize(String serverUuid, String requestedPath) {
        return listing.getDirectorySize(serverUuid, requestedPath);
    }

    public String buildZipArchiveName(String path) {
        return archive.buildZipArchiveName(path);
    }
}
