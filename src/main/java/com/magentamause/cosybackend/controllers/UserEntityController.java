package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.PasswordUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
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
    @RequireAccess(action = Action.READ, resource = Resource.USER)
    public ResponseEntity<List<UserEntityDto>> getAllUserEntities() {
        List<UserEntity> users = userEntityService.getAllUsers();
        List<UserEntityDto> userDTOs =
                users.stream().map(UserEntity::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }

    @GetMapping("/{uuid}")
    @RequireAccess(action = Action.READ, resource = Resource.USER)
    public ResponseEntity<UserEntityDto> getUserEntity(@PathVariable @ResourceId String uuid) {
        UserEntity user = userEntityService.getUserByUuid(uuid);
        return ResponseEntity.ok(user.toDto());
    }

    @GetMapping("/username/{username}")
    @RequireAccess(action = Action.READ, resource = Resource.USER)
    public ResponseEntity<UserEntityDto> getUserEntityByUsername(@PathVariable @ResourceId String username) {
        UserEntity user = userEntityService.getUserByUsername(username);
        return ResponseEntity.ok(user.toDto());
    }

    @DeleteMapping("/{uuid}")
    @RequireAccess(action = Action.DELETE, resource = Resource.USER)
    public ResponseEntity<Void> deleteUserEntity(@PathVariable @ResourceId String uuid) {
        userEntityService.deleteUserByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/change-password")
    @RequireAccess(action = Action.UPDATE, resource = Resource.USER)
    public ResponseEntity<UserEntityDto> changePassword(
            @PathVariable @ResourceId String uuid, @Valid @RequestBody PasswordUpdateDto request) {
        log.info("Changing password for user with UUID: {}", uuid);
        UserEntity user = userEntityService.getUserByUuid(uuid);
        UserEntity userWithChangedPassword =
                userEntityService.changePassword(
                        user, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(userWithChangedPassword.toDto());
    }
}
