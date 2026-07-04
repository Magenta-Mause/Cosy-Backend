package com.magentamause.cosybackend.controllers.api;

import com.magentamause.cosybackend.dtos.actiondtos.user.UserCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserInviteCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserInviteDto;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "User Invites", description = "User invitation management")
@RequestMapping("/user-invites")
public interface UserInviteApi {

    @Operation(summary = "Get all user invites")
    @ApiResponse(responseCode = "200", description = "Invites returned")
    @GetMapping
    ResponseEntity<List<UserInviteDto>> getAllUserInvites();

    @Operation(summary = "Get invite by secret key")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invite found"),
        @ApiResponse(responseCode = "404", description = "Invite not found")
    })
    @GetMapping("/{secretKey}")
    ResponseEntity<UserInviteDto> getUserInvite(
            @Parameter(description = "Invite secret key") @PathVariable String secretKey);

    @Operation(summary = "Create a new invite")
    @ApiResponse(responseCode = "201", description = "Invite created")
    @PostMapping
    ResponseEntity<UserInviteDto> createInvite(
            @Valid @RequestBody UserInviteCreationDto userInviteCreationDto);

    @Operation(summary = "Use an invite to register a new user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User created"),
        @ApiResponse(responseCode = "404", description = "Invite not found or expired")
    })
    @PostMapping("/use/{secretKey}")
    ResponseEntity<UserEntityDto> useInvite(
            @Parameter(description = "Invite secret key") @PathVariable String secretKey,
            @Valid @RequestBody UserCreationDto user);

    @Operation(summary = "Revoke an invite")
    @ApiResponse(responseCode = "204", description = "Invite revoked")
    @DeleteMapping("/{uuid}")
    ResponseEntity<Void> revokeInvite(
            @Parameter(description = "Invite UUID") @PathVariable String uuid);
}
