package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerFileSystemDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.gameserver.GameServerMountService;
import com.magentamause.cosybackend.services.gameserver.GameServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerController {

  private final GameServerService gameServerService;
  private final GameServerMountService gameServerMountService;
  private final SecurityContextService securityContextService;

  @GetMapping
  @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
  public ResponseEntity<List<GameServerDto>> getAllGameServers() {
    List<GameServerDto> dtos = gameServerService.getAllGameServers().stream()
        .map(GameServerEntity::toDto)
        .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{uuid}")
  @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
  public ResponseEntity<GameServerDto> getGameServerById(@PathVariable @ResourceId String uuid) {
    GameServerEntity entity = gameServerService.getGameServerById(uuid);
    return ResponseEntity.ok(entity.toDto());
  }

  @DeleteMapping("/{uuid}")
  @RequireAccess(action = Action.DELETE, resource = Resource.GAME_SERVER)
  public ResponseEntity<Void> deleteGameServerById(@PathVariable @ResourceId String uuid) {
    gameServerService.deleteGameServerById(uuid);
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  @RequireAccess(action = Action.CREATE, resource = Resource.GAME_SERVER)
  public ResponseEntity<GameServerDto> createGameServer(
      @Valid @RequestBody GameServerCreationDto gameServerCreationDto) {
    log.info("Creating game server {}", gameServerCreationDto);
    UserEntity user = securityContextService.getUser();

    GameServerEntity createdGameServer = gameServerService.convertDtoToEntity(gameServerCreationDto);
    createdGameServer.setOwner(user);

    gameServerService.saveGameServer(createdGameServer);
    return ResponseEntity.status(201).body(createdGameServer.toDto());
  }

  @PutMapping("/{uuid}")
  @RequireAccess(action = Action.UPDATE, resource = Resource.GAME_SERVER)
  public ResponseEntity<GameServerDto> updateGameServer(
      @PathVariable @ResourceId String uuid,
      @Valid @RequestBody GameServerUpdateDto updateDto) {
    log.info("Received request to update the game server with id {}", uuid);

    GameServerEntity updated = gameServerService.updateGameServerConfiguration(uuid, updateDto);

    return ResponseEntity.ok(updated.toDto());
  }

  @GetMapping("/{uuid}/status")
  @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
  public ResponseEntity<GameServerDto.GameServerStatus> getServiceInfo(
      @PathVariable String uuid) {
    return ResponseEntity.ok(gameServerService.getStatus(uuid));
  }

  @PostMapping(value = "/{uuid}/start")
  @RequireAccess(action = Action.START_STOP, resource = Resource.GAME_SERVER)
  public ResponseEntity<Void> startService(@PathVariable @ResourceId String uuid) {
    gameServerService.startServer(uuid);

    return ResponseEntity.accepted().build();
  }

  @PostMapping("/{uuid}/stop")
  @RequireAccess(action = Action.START_STOP, resource = Resource.GAME_SERVER)
  public ResponseEntity<Void> stopService(@PathVariable @ResourceId String uuid) {
    gameServerService.stopServer(uuid);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{uuid}/file-system/{volumeUuid}")
  @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
  public ResponseEntity<GameServerFileSystemDto> getFileSystemForVolume(
      @PathVariable @ResourceId String uuid,
      @PathVariable @NotBlank String volumeUuid,
      @RequestParam(name = "path", required = false, defaultValue = "") String path,
      @RequestParam(name = "fetch_depth", defaultValue = "1") @Min(0) int fetchDepth) {
    GameServerFileSystemDto dto = gameServerMountService.readBindMountFileSystem(uuid, volumeUuid, path, fetchDepth);
    return ResponseEntity.ok(dto);
  }

  @RequestMapping(value = "/{uuid}/file-system/{volumeUuid}/file", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
  // We have to specify this so orval generates reasonable typescript types for
  // this response
  @Operation(summary = "Read a file from a bind mount volume", responses = {
      @ApiResponse(responseCode = "200", description = "File content", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary", description = "Raw file bytes")))
  })
  public ResponseEntity<byte[]> readFileFromVolume(
      @PathVariable @ResourceId String uuid,
      @PathVariable @NotBlank String volumeUuid,
      @RequestParam("path") @NotBlank String path) {
    byte[] content = gameServerMountService.readFileFromBindMountVolume(uuid, volumeUuid, path);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(content);
  }
}
