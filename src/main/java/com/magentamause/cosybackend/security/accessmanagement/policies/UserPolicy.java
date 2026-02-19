package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
public class UserPolicy {

    @Validates(Operation.USER_GET_ALL)
    public static boolean canGetAllUsers(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return false;
    }

    @Validates(Operation.USER_GET_BY_UUID)
    public static boolean canGetUserByUuid(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_GET_BY_USERNAME)
    public static boolean canGetUserByUsername(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return true;
    }

    @Validates(Operation.USER_UPDATE)
    public static boolean canUpdateUser(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_CHANGE_PASSWORD)
    public static boolean canChangePassword(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_CHANGE_PASSWORD_BY_ADMIN)
    public static boolean canChangePasswordByAdmin(
            ResourceResolver resolver, Object referenceId, UserEntity user) {

        UserEntity userOfPassword = resolver.getUserEntity((String) referenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if(user.getRole().equals(UserEntity.Role.OWNER)) {
            return true;
        }else if(user.getUuid().equals(userOfPassword.getUuid())) {
            return true;
        }else if(userOfPassword.getRole().isAdmin()) {
            return false;
        }else {
            return user.getRole().isAdmin();
        }
    }

    @Validates(Operation.USER_DELETE)
    public static boolean canDeleteUser(
            ResourceResolver resolver, Object referenceId, UserEntity user) {

        UserEntity userToDelete = resolver.getUserEntity((String) referenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if(user.getRole().equals(UserEntity.Role.OWNER)) {
            return true;
        }else if(user.getUuid().equals(userToDelete.getUuid())) {
            return true;
        }else if(userToDelete.getRole().isAdmin()) {
            return false;
        }else {
            return user.getRole().isAdmin();
        }
    }

    @Validates(Operation.USER_READ_PERMISSIONS)
    public static boolean canReadPermissions(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }
}
