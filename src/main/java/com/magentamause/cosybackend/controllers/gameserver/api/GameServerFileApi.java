package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.entitydtos.DirectorySizeDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(
        name = "Game Server File System",
        description = "File system operations on game server bind mounts")
@RequestMapping("/game-server/{uuid}/file-system")
public interface GameServerFileApi {

    @Operation(summary = "List the file system tree at a given path")
    @ApiResponse(responseCode = "200", description = "File system tree returned")
    @GetMapping("/")
    ResponseEntity<GameServerFileSystemDto> getFileSystemForVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam(name = "path", required = false, defaultValue = "") String path,
            @RequestParam(name = "fetch_depth", defaultValue = "1") @Min(0) @Max(5) int fetchDepth);

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
    @RequestMapping(
            value = "/file",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> readFileFromVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path);

    @Operation(
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
    @RequestMapping(
            value = "/upload",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<Void> uploadFileToVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path,
            @RequestBody byte[] fileContent);

    @Operation(
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
    @GetMapping(value = "/download-as-zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<StreamingResponseBody> downloadDirectoryAsZip(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path);

    @Operation(summary = "Create a directory in a bind mount volume")
    @ApiResponse(responseCode = "201", description = "Directory created")
    @PostMapping("/mkdir")
    ResponseEntity<Void> createDirectoryInVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path);

    @Operation(summary = "Rename a file or directory in a bind mount volume")
    @ApiResponse(responseCode = "200", description = "Renamed successfully")
    @PostMapping("/rename")
    ResponseEntity<Void> renameInVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("oldPath") @NotBlank String oldPath,
            @RequestParam("newPath") @NotBlank String newPath);

    @Operation(summary = "Delete a file or directory in a bind mount volume")
    @ApiResponse(responseCode = "200", description = "Deleted successfully")
    @PostMapping("/delete")
    ResponseEntity<Void> deleteInVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path);

    @Operation(
            summary = "Upload and extract a zip archive into a bind mount volume",
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
                                                            description = "Raw zip bytes"))),
            responses = {
                @ApiResponse(responseCode = "200", description = "Archive extracted successfully")
            })
    @RequestMapping(
            value = "/upload-archive",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<Void> uploadArchiveToVolume(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path,
            @RequestParam(value = "clear", defaultValue = "false") boolean clear,
            @RequestBody byte[] zipBytes);

    @Operation(summary = "Get the total uncompressed size of a directory or file")
    @ApiResponse(responseCode = "200", description = "Size returned")
    @GetMapping("/directory-size")
    ResponseEntity<DirectorySizeDto> getDirectorySize(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestParam("path") @NotBlank String path);
}
