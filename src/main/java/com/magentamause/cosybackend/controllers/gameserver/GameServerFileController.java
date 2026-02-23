package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.core.gameserver.GameServerMountService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server/{uuid}/file-system")
public class GameServerFileController {
    private final GameServerMountService gameServerMountService;

    @GetMapping("/")
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    public ResponseEntity<GameServerFileSystemDto> getFileSystemForVolume(
            @PathVariable @ResourceId String uuid,
            @RequestParam(name = "path", required = false, defaultValue = "") String path,
            @RequestParam(name = "fetch_depth", defaultValue = "1") @Min(0) @Max(5)
                    int fetchDepth) {
        GameServerFileSystemDto dto =
                gameServerMountService.readBindMountFileSystem(uuid, path, fetchDepth);
        return ResponseEntity.ok(dto);
    }

    // TODO: consider adding limit / pagination support later
    @RequestMapping(
            value = "/file",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    // We have to specify this so orval generates reasonable typescript types for
    // this response
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Read a file from a bind mount volume",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "File content",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                        schema =
                                                @Schema(
                                                        type = "string",
                                                        format = "binary",
                                                        description = "Raw file bytes")))
            })
    public ResponseEntity<byte[]> readFileFromVolume(
            @PathVariable @ResourceId String uuid, @RequestParam("path") @NotBlank String path) {
        byte[] content = gameServerMountService.readFileFromBindMountVolume(uuid, path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(content);
    }

    @RequestMapping(
            value = "/upload",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Upload a file to a bind mount volume",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                            schema =
                                                    @Schema(
                                                            type = "string",
                                                            format = "binary",
                                                            description =
                                                                    "Raw file bytes to upload"))),
            responses = {
                @ApiResponse(responseCode = "200", description = "File uploaded successfully")
            })
    public ResponseEntity<Void> uploadFileToVolume(
            @PathVariable @ResourceId String uuid,
            @RequestParam("path") @NotBlank String path,
            @RequestBody byte[] fileContent) {

        gameServerMountService.uploadFileToBindMountVolume(uuid, path, fileContent);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/download-as-zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @NeedsValidation(Operation.GAME_SERVER_FILES_READ)
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Download a directory as a zip archive",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Zip archive of the directory",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                        schema =
                                                @Schema(
                                                        type = "string",
                                                        format = "binary",
                                                        description = "Zip archive bytes")))
            })
    public ResponseEntity<StreamingResponseBody> downloadDirectoryAsZip(
            @PathVariable @ResourceId String uuid, @RequestParam("path") @NotBlank String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.isBlank()) name = "archive";

        StreamingResponseBody body =
                outputStream -> gameServerMountService.streamDirectoryAsZip(uuid, path, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".zip\"")
                .body(body);
    }

    @PostMapping("/mkdir")
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> createDirectoryInVolume(
            @PathVariable @ResourceId String uuid, @RequestParam("path") @NotBlank String path) {
        gameServerMountService.createDirectoryInBindMountVolume(uuid, path);
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/rename")
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> renameInVolume(
            @PathVariable @ResourceId String uuid,
            @RequestParam("oldPath") @NotBlank String oldPath,
            @RequestParam("newPath") @NotBlank String newPath) {
        gameServerMountService.renameInBindMountVolume(uuid, oldPath, newPath);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete")
    @NeedsValidation(Operation.GAME_SERVER_FILES_UPDATE)
    public ResponseEntity<Void> deleteInVolume(
            @PathVariable @ResourceId String uuid, @RequestParam("path") @NotBlank String path) {
        gameServerMountService.deleteInBindMountVolume(uuid, path);
        return ResponseEntity.ok().build();
    }
}
