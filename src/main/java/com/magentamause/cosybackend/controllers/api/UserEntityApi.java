package com.magentamause.cosybackend.controllers.api;

import com.magentamause.cosybackend.dtos.actiondtos.user.PasswordUpdateByAdminDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.PasswordUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserCanCreateGameServersDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserDockerLimitsUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserRoleUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "User Entity", description = "User management")
@RequestMapping("user-entity")
public interface UserEntityApi {

    @Operation(summary = "Get all users")
    @ApiResponse(responseCode = "200", description = "Users returned")
    @GetMapping
    ResponseEntity<List<UserEntityDto>> getAllUserEntities();

    @Operation(summary = "Get user by UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{uuid}")
    ResponseEntity<UserEntityDto> getUserEntity(
            @Parameter(description = "User UUID") @PathVariable String uuid);

    @Operation(summary = "Delete user by UUID")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @DeleteMapping("/{uuid}")
    ResponseEntity<Void> deleteUserEntity(
            @Parameter(description = "User UUID") @PathVariable String uuid);

    @Operation(summary = "Change user password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password changed"),
        @ApiResponse(responseCode = "401", description = "Old password incorrect")
    })
    @PatchMapping("/{uuid}/change-password")
    ResponseEntity<UserEntityDto> changePassword(
            @Parameter(description = "User UUID") @PathVariable String uuid,
            @Valid @RequestBody PasswordUpdateDto request);

    @Operation(summary = "Change user password as admin")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @PatchMapping("/{uuid}/change-password-by-admin")
    ResponseEntity<UserEntityDto> changePasswordByAdmin(
            @Parameter(description = "User UUID") @PathVariable String uuid,
            @Valid @RequestBody PasswordUpdateByAdminDto request);

    @Operation(summary = "Update Docker resource limits for a user")
    @ApiResponse(responseCode = "200", description = "Limits updated")
    @PatchMapping("/{uuid}/docker-limits")
    ResponseEntity<UserEntityDto> updateDockerLimits(
            @Parameter(description = "User UUID") @PathVariable String uuid,
            @Valid @RequestBody UserDockerLimitsUpdateDto request);

    @Operation(summary = "Change user role")
    @ApiResponse(responseCode = "200", description = "Role changed")
    @PatchMapping("/{uuid}/change-role")
    ResponseEntity<UserEntityDto> changeRole(
            @Parameter(description = "User UUID") @PathVariable String uuid,
            @Valid @RequestBody UserRoleUpdateDto request);

    @Operation(summary = "Toggle whether a user can create game servers")
    @ApiResponse(responseCode = "200", description = "Permission updated")
    @PatchMapping("/{uuid}/can-create-game-servers")
    ResponseEntity<UserEntityDto> setCanCreateGameServers(
            @Parameter(description = "User UUID") @PathVariable String uuid,
            @Valid @RequestBody UserCanCreateGameServersDto request);

    @Operation(summary = "Get user UUID by username")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "UUID returned"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/uuid-by-username/{username}")
    ResponseEntity<String> getUUIDByUsername(
            @Parameter(description = "Username") @PathVariable String username);
}
