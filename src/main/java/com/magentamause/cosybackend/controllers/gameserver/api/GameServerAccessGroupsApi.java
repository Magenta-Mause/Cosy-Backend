package com.magentamause.cosybackend.controllers.gameserver.api;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerAccessGroupDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Game Server Access Groups", description = "Access group configuration for game servers")
@RequestMapping("/game-server")
public interface GameServerAccessGroupsApi {

    @Operation(summary = "Create an access group for a game server")
    @ApiResponse(responseCode = "200", description = "Access group created")
    @PostMapping("/{uuid}/access-groups")
    ResponseEntity<GameServerAccessGroupDto> createGameServerAccessGroup(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @Valid @RequestBody AccessGroupCreationDto creationDto);

    @Operation(summary = "Delete an access group from a game server")
    @ApiResponse(responseCode = "204", description = "Access group deleted")
    @DeleteMapping("/{uuid}/access-groups/{access_group_uuid}")
    ResponseEntity<Void> deleteGameServerAccessGroup(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @PathVariable("access_group_uuid") String accessGroupUuid);

    @Operation(summary = "Update an access group on a game server")
    @ApiResponse(responseCode = "200", description = "Access groups returned")
    @PatchMapping("/{uuid}/access-groups/{access_group_uuid}")
    ResponseEntity<List<GameServerAccessGroupDto>> updateGameServerAccessGroups(
            @Parameter(description = "Game server UUID") @PathVariable String uuid,
            @PathVariable("access_group_uuid") String accessGroupUuid,
            @Valid @RequestBody AccessGroupUpdateDto updateDto);
}
