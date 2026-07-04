package com.magentamause.cosybackend.controllers.gameserver.impl;

import com.magentamause.cosybackend.controllers.gameserver.api.GameServerFileApi;
import com.magentamause.cosybackend.dtos.entitydtos.DirectorySizeDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerMountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameServerFileController implements GameServerFileApi {
    private final GameServerMountService gameServerMountService;

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    public ResponseEntity<GameServerFileSystemDto> getFileSystemForVolume(
            @ResourceId String uuid, String path, int fetchDepth) {

        GameServerFileSystemDto dto =
                gameServerMountService.readBindMountFileSystem(uuid, path, fetchDepth);
        return ResponseEntity.ok(dto);
    }

    // TODO: consider adding limit / pagination support later
    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    public ResponseEntity<byte[]> readFileFromVolume(@ResourceId String uuid, String path) {
        byte[] content = gameServerMountService.readFileFromBindMountVolume(uuid, path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(content);
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> uploadFileToVolume(
            @ResourceId String uuid, String path, byte[] fileContent) {

        gameServerMountService.uploadFileToBindMountVolume(uuid, path, fileContent);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    public ResponseEntity<StreamingResponseBody> downloadDirectoryAsZip(
            @ResourceId String uuid, String path) {

        String zipName = gameServerMountService.buildZipArchiveName(path);
        StreamingResponseBody body =
                outputStream ->
                        gameServerMountService.streamDirectoryAsZip(uuid, path, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + zipName + ".zip\"")
                .body(body);
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> createDirectoryInVolume(@ResourceId String uuid, String path) {
        gameServerMountService.createDirectoryInBindMountVolume(uuid, path);
        return ResponseEntity.status(201).build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> renameInVolume(
            @ResourceId String uuid, String oldPath, String newPath) {
        gameServerMountService.renameInBindMountVolume(uuid, oldPath, newPath);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> deleteInVolume(@ResourceId String uuid, String path) {
        gameServerMountService.deleteInBindMountVolume(uuid, path);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> uploadArchiveToVolume(
            @ResourceId String uuid, String path, boolean clear, byte[] zipBytes) {
        gameServerMountService.extractArchiveToBindMount(uuid, path, zipBytes, clear);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    public ResponseEntity<DirectorySizeDto> getDirectorySize(@ResourceId String uuid, String path) {
        return ResponseEntity.ok(gameServerMountService.getDirectorySize(uuid, path));
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> setPermissions(
            @ResourceId String uuid, String path, int mode, Integer uid) {
        gameServerMountService.setFilePermissions(uuid, path, mode, uid);
        return ResponseEntity.ok().build();
    }

    @Override
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    public ResponseEntity<StreamingResponseBody> downloadDirectoryAsZipChunk(
            @ResourceId String uuid, String path, int chunkIndex, int chunkSizeMb) {

        int totalChunks = gameServerMountService.countZipChunks(uuid, path, chunkSizeMb);
        String zipName = gameServerMountService.buildZipArchiveName(path);
        String partName = zipName + "-part-" + (chunkIndex + 1) + "-of-" + totalChunks + ".zip";

        StreamingResponseBody body =
                outputStream ->
                        gameServerMountService.streamDirectoryAsZipChunk(
                                uuid, path, chunkIndex, chunkSizeMb, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + partName + "\"")
                .header("X-Total-Chunks", String.valueOf(totalChunks))
                .body(body);
    }
}
