package com.magentamause.cosybackend.controllers.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.gameserver.GameServerMountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server/{uuid}/file-system")
public class FileController {
    private final GameServerMountService gameServerMountService;

    @GetMapping("/")
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER_FILES)
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
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER_FILES)
    // We have to specify this so orval generates reasonable typescript types for
    // this response
    @Operation(
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
}
