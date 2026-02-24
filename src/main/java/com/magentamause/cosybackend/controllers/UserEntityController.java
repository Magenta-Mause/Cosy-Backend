package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.user.PasswordUpdateByAdminDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.PasswordUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserDockerLimitsUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.user.UserEntityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("user-entity")
@Slf4j
public class UserEntityController {

    private final UserEntityService userEntityService;

    @GetMapping
    @NeedsValidation(Operation.USER_GET_ALL)
    public ResponseEntity<List<UserEntityDto>> getAllUserEntities() {
        List<UserEntity> users = userEntityService.getAllUsers();
        List<UserEntityDto> userDTOs =
                users.stream().map(UserEntity::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }

    @GetMapping("/{uuid}")
    @NeedsValidation(Operation.USER_GET_BY_UUID)
    public ResponseEntity<UserEntityDto> getUserEntity(@PathVariable @ResourceId String uuid) {
        UserEntity user = userEntityService.getUserByUuid(uuid);
        return ResponseEntity.ok(user.toDto());
    }

    @DeleteMapping("/{uuid}")
    @NeedsValidation(Operation.USER_DELETE)
    public ResponseEntity<Void> deleteUserEntity(@PathVariable @ResourceId String uuid) {
        userEntityService.deleteUserByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/change-password")
    @NeedsValidation(Operation.USER_CHANGE_PASSWORD)
    public ResponseEntity<UserEntityDto> changePassword(
            @PathVariable @ResourceId String uuid, @Valid @RequestBody PasswordUpdateDto request) {
        UserEntity userWithChangedPassword =
                userEntityService.changePassword(
                        uuid, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(userWithChangedPassword.toDto());
    }

    @PatchMapping("/{uuid}/change-password-by-admin")
    @NeedsValidation(Operation.USER_CHANGE_PASSWORD_BY_ADMIN)
    public ResponseEntity<UserEntityDto> changePasswordByAdmin(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody PasswordUpdateByAdminDto request) {
        UserEntity userWithChangedPassword =
                userEntityService.changePasswordByAdmin(uuid, request.getNewPassword());
        return ResponseEntity.ok(userWithChangedPassword.toDto());
    }

    @PatchMapping("/{uuid}/docker-limits")
    @NeedsValidation(Operation.USER_UPDATE_DOCKER_LIMITS)
    public ResponseEntity<UserEntityDto> updateDockerLimits(
            @PathVariable @ResourceId String uuid,
            @Valid @RequestBody UserDockerLimitsUpdateDto request) {
        UserEntity updatedUser =
                userEntityService.updateDockerLimits(uuid, request.getDockerHardwareLimits());
        return ResponseEntity.ok(updatedUser.toDto());
    }

    @GetMapping("/uuid-by-username/{username}")
    public ResponseEntity<String> getUUIDByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userEntityService.getUserByUsername(username).getUuid());
    }
}
