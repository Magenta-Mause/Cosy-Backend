package com.magentamause.cosybackend.controllers.impl;

import com.magentamause.cosybackend.controllers.api.UserEntityApi;
import com.magentamause.cosybackend.dtos.actiondtos.user.PasswordUpdateByAdminDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.PasswordUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserCanCreateGameServersDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserDockerLimitsUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.user.UserRoleUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.user.UserEntityService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserEntityController implements UserEntityApi {

    private final UserEntityService userEntityService;

    @Override
    @NeedsValidation(Operation.USER_GET_ALL)
    public ResponseEntity<List<UserEntityDto>> getAllUserEntities() {
        List<UserEntity> users = userEntityService.getAllUsers();
        List<UserEntityDto> userDTOs =
                users.stream().map(UserEntity::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }

    @Override
    @NeedsValidation(Operation.USER_GET_BY_UUID)
    public ResponseEntity<UserEntityDto> getUserEntity(@ResourceId String uuid) {
        UserEntity user = userEntityService.getUserByUuid(uuid);
        return ResponseEntity.ok(user.toDto());
    }

    @Override
    @NeedsValidation(Operation.USER_DELETE)
    public ResponseEntity<Void> deleteUserEntity(@ResourceId String uuid) {
        userEntityService.deleteUserByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    @NeedsValidation(Operation.USER_CHANGE_PASSWORD)
    public ResponseEntity<UserEntityDto> changePassword(
            @ResourceId String uuid, PasswordUpdateDto request) {
        UserEntity userWithChangedPassword =
                userEntityService.changePassword(
                        uuid, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(userWithChangedPassword.toDto());
    }

    @Override
    @NeedsValidation(Operation.USER_CHANGE_PASSWORD_BY_ADMIN)
    public ResponseEntity<UserEntityDto> changePasswordByAdmin(
            @ResourceId String uuid, PasswordUpdateByAdminDto request) {
        UserEntity userWithChangedPassword =
                userEntityService.changePasswordByAdmin(uuid, request.getNewPassword());
        return ResponseEntity.ok(userWithChangedPassword.toDto());
    }

    @Override
    @NeedsValidation(Operation.USER_UPDATE_DOCKER_LIMITS)
    public ResponseEntity<UserEntityDto> updateDockerLimits(
            @ResourceId String uuid, UserDockerLimitsUpdateDto request) {
        UserEntity updatedUser =
                userEntityService.updateDockerLimits(uuid, request.getDockerHardwareLimits());
        return ResponseEntity.ok(updatedUser.toDto());
    }

    @Override
    @NeedsValidation(Operation.USER_CHANGE_ROLE)
    public ResponseEntity<UserEntityDto> changeRole(
            @ResourceId String uuid, UserRoleUpdateDto request) {
        UserEntity updatedUser = userEntityService.changeRole(uuid, request.getRole());
        return ResponseEntity.ok(updatedUser.toDto());
    }

    @Override
    @NeedsValidation(Operation.USER_TOGGLE_CAN_CREATE_GAME_SERVERS)
    public ResponseEntity<UserEntityDto> setCanCreateGameServers(
            @ResourceId String uuid, UserCanCreateGameServersDto request) {
        UserEntity updatedUser =
                userEntityService.setCanCreateGameServers(uuid, request.getCanCreateGameServers());
        return ResponseEntity.ok(updatedUser.toDto());
    }

    @Override
    public ResponseEntity<String> getUUIDByUsername(String username) {
        return ResponseEntity.ok(userEntityService.getUserByUsername(username).getUuid());
    }
}
