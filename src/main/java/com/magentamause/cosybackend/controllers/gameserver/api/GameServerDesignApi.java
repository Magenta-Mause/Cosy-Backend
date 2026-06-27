package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerDesignUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
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

@Tag(name = "Game Server Design", description = "Game server visual design configuration")
@RequestMapping("/game-server")
public interface GameServerDesignApi {

    @Operation(summary = "Update the design of a game server")
    @ApiResponse(responseCode = "200", description = "Design updated")
    @PatchMapping("/{uuid}/design")
    ResponseEntity<GameServerDto> updateDesign(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody GameServerDesignUpdateDto updateDto);
}
