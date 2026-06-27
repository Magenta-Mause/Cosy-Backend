package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Game Server Permissions", description = "Game server permission queries")
@RequestMapping("/game-server")
public interface GameServerPermissionsApi {

    @Operation(summary = "Get the current user's permissions for a game server")
    @ApiResponse(responseCode = "200", description = "Permissions returned")
    @GetMapping("/{uuid}/permissions")
    ResponseEntity<List<GameServerAccessPermission>> getUserPermissions(
            @Parameter(description = "Game server UUID") @PathVariable String uuid);
}
