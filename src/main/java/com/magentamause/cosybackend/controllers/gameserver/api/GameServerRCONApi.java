package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Game Server RCON", description = "RCON configuration for game servers")
@RequestMapping("/game-server")
public interface GameServerRCONApi {

    @Operation(summary = "Update RCON configuration for a game server")
    @ApiResponse(responseCode = "200", description = "RCON configuration updated")
    @PatchMapping("/{uuid}/rcon-configuration")
    ResponseEntity<GameServerDto> updateRconConfiguration(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody RCONConfiguration updateDto);
}
