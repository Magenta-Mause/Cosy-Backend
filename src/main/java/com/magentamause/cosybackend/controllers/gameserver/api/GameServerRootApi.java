package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.actiondtos.TransferOwnershipDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.SendCommandDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Game Server", description = "Game server lifecycle management")
@RequestMapping("/game-server")
public interface GameServerRootApi {

    @Operation(summary = "List all game servers visible to the current user")
    @ApiResponse(responseCode = "200", description = "Game servers returned")
    @GetMapping
    ResponseEntity<List<GameServerDto>> getAllGameServers();

    @Operation(summary = "Get game server by UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Game server found"),
        @ApiResponse(responseCode = "404", description = "Game server not found")
    })
    @GetMapping("/{uuid}")
    ResponseEntity<GameServerDto> getGameServerById(
            @Parameter(description = "Game server UUID") @PathVariable String uuid);

    @Operation(summary = "Delete game server by UUID")
    @ApiResponse(responseCode = "204", description = "Game server deleted")
    @DeleteMapping("/{uuid}")
    ResponseEntity<Void> deleteGameServerById(
            @Parameter(description = "Game server UUID") @PathVariable String uuid);

    @Operation(summary = "Create a new game server")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Game server created"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration")
    })
    @PostMapping
    ResponseEntity<GameServerDto> createGameServer(
            @Valid @RequestBody GameServerCreationDto gameServer);

    @Operation(summary = "Update game server configuration")
    @ApiResponse(responseCode = "200", description = "Game server updated")
    @PutMapping("/{uuid}")
    ResponseEntity<GameServerDto> updateGameServer(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody GameServerUpdateDto updateDto);

    @Operation(summary = "Get current status of a game server")
    @ApiResponse(responseCode = "200", description = "Status returned")
    @GetMapping("/{uuid}/status")
    ResponseEntity<GameServerDto.GameServerStatus> getServiceInfo(
            @Parameter(description = "Game server UUID") @PathVariable String uuid);

    @Operation(summary = "Start a game server")
    @ApiResponse(responseCode = "202", description = "Start accepted")
    @PostMapping("/{uuid}/start")
    ResponseEntity<Void> startService(
            @Parameter(description = "Game server UUID") @PathVariable String uuid);

    @Operation(summary = "Stop a game server")
    @ApiResponse(responseCode = "200", description = "Stop accepted")
    @PostMapping("/{uuid}/stop")
    ResponseEntity<Void> stopService(
            @Parameter(description = "Game server UUID") @PathVariable String uuid);

    @Operation(summary = "Send a console command to a game server")
    @ApiResponse(responseCode = "204", description = "Command sent")
    @PostMapping("/{uuid}/send-command")
    ResponseEntity<Void> sendCommand(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @RequestBody SendCommandDto command);

    @Operation(summary = "Transfer game server ownership to another user")
    @ApiResponse(responseCode = "200", description = "Ownership transferred")
    @PostMapping("/{uuid}/transfer-ownership")
    ResponseEntity<GameServerDto> transferOwnership(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody TransferOwnershipDto transferOwnershipDto);
}
