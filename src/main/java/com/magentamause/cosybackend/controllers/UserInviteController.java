package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.user.UserCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserInviteCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserInviteDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.UserInviteEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import com.magentamause.cosybackend.services.user.UserInviteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user-invites")
@Slf4j
public class UserInviteController {

    private final UserInviteService userInviteService;
    private final SecurityContextService securityContextService;
    private final UserEntityService userEntityService;

    @GetMapping
    @NeedsValidation(Operation.USER_INVITE_READ)
    public ResponseEntity<List<UserInviteDto>> getAllUserInvites() {
        return ResponseEntity.ok(
                userInviteService.getAllInvites().stream()
                        .map(UserInviteEntity::convertToDto)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/{secretKey}")
    public ResponseEntity<UserInviteDto> getUserInvite(
            @PathVariable("secretKey") String secretKey) {
        return ResponseEntity.ok(
                userInviteService.getInviteBySecretKeyOrElseThrow(secretKey).convertToDto());
    }

    @PostMapping
    @NeedsValidation(Operation.USER_INVITE_CREATE)
    public ResponseEntity<UserInviteDto> createInvite(
            @Valid @RequestBody UserInviteCreationDto userInviteCreationDto) {
        log.info("Creating invite for {}", userInviteCreationDto);
        String inviterUuid = securityContextService.getUserId();
        UserInviteEntity userInvite =
                userInviteService.createInvite(inviterUuid, userInviteCreationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userInvite.convertToDto());
    }

    @PostMapping("/use/{secretKey}")
    public ResponseEntity<UserEntityDto> useInvite(
            @PathVariable String secretKey, @Valid @RequestBody UserCreationDto user) {
        UserEntity createdUser =
                userInviteService.useInvite(secretKey, user.getUsername(), user.getPassword());
        return ResponseEntity.ok(createdUser.toDto());
    }

    @DeleteMapping("/{uuid}")
    @NeedsValidation(Operation.USER_INVITE_DELETE)
    public ResponseEntity<Void> revokeInvite(@PathVariable String uuid) {
        userInviteService.revokeInvite(uuid);
        return ResponseEntity.noContent().build();
    }
}
