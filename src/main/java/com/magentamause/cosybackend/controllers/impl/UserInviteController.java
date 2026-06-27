package com.magentamause.cosybackend.controllers.impl;

import com.magentamause.cosybackend.controllers.api.UserInviteApi;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserInviteCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserInviteDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.UserInviteEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.services.auth.SecurityContextService;
import com.magentamause.cosybackend.services.user.UserInviteService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserInviteController implements UserInviteApi {

    private final UserInviteService userInviteService;
    private final SecurityContextService securityContextService;

    @Override
    @NeedsValidation(Operation.USER_INVITE_READ)
    public ResponseEntity<List<UserInviteDto>> getAllUserInvites() {
        return ResponseEntity.ok(
                userInviteService.getAllInvites().stream()
                        .map(UserInviteEntity::convertToDto)
                        .collect(Collectors.toList()));
    }

    @Override
    public ResponseEntity<UserInviteDto> getUserInvite(String secretKey) {
        return ResponseEntity.ok(
                userInviteService.getInviteBySecretKeyOrElseThrow(secretKey).convertToDto());
    }

    @Override
    @NeedsValidation(Operation.USER_INVITE_CREATE)
    public ResponseEntity<UserInviteDto> createInvite(
            UserInviteCreationDto userInviteCreationDto) {
        log.info("Creating invite for {}", userInviteCreationDto);
        String inviterUuid = securityContextService.getUserId();
        UserInviteEntity userInvite =
                userInviteService.createInvite(inviterUuid, userInviteCreationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userInvite.convertToDto());
    }

    @Override
    public ResponseEntity<UserEntityDto> useInvite(
            String secretKey, UserCreationDto user) {
        UserEntity createdUser =
                userInviteService.useInvite(secretKey, user.getUsername(), user.getPassword());
        return ResponseEntity.ok(createdUser.toDto());
    }

    @Override
    @NeedsValidation(Operation.USER_INVITE_DELETE)
    public ResponseEntity<Void> revokeInvite(String uuid) {
        userInviteService.revokeInvite(uuid);
        return ResponseEntity.noContent().build();
    }
}
