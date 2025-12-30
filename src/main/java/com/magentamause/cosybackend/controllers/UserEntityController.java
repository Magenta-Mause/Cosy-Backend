package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.UserEntityService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("user-entity")
public class UserEntityController {

    private final UserEntityService userEntityService;

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.USER)
    public ResponseEntity<List<UserEntityDto>> getAllUserEntities() {
        List<UserEntity> users = userEntityService.getAllUsers();
        List<UserEntityDto> userDTOs =
                users.stream().map(userEntityService::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }

    @GetMapping("/{uuid}")
    @RequireAccess(action = Action.READ, resource = Resource.USER)
    public ResponseEntity<UserEntityDto> getUserEntity(@PathVariable @ResourceId String uuid) {
        UserEntity user = userEntityService.getUserByUuid(uuid);
        return ResponseEntity.ok(userEntityService.convertToDTO(user));
    }

    @DeleteMapping("/{uuid}")
    @RequireAccess(action = Action.DELETE, resource = Resource.USER)
    public ResponseEntity<Void> deleteUserEntity(@PathVariable @ResourceId String uuid) {
        userEntityService.deleteUserByUuid(uuid);
        return ResponseEntity.noContent().build();
    }
}
