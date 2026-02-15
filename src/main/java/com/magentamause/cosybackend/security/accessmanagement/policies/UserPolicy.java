package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPolicy {

    @Validates(Operation.USER_GET_ALL)
    public boolean getAllUsers(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return false;
    }

    @Validates(Operation.USER_GET_BY_UUID)
    public boolean getUserByUuid(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_GET_BY_USERNAME)
    public boolean getUserByUsername(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return true;
    }

    @Validates(Operation.USER_UPDATE)
    public boolean updateUser(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_CHANGE_PASSWORD)
    public boolean canChangePassword(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_DELETE)
    public boolean deleteUser(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }

    @Validates(Operation.USER_READ_PERMISSIONS)
    public boolean readPermissions(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return user.getUuid().equals(referenceId);
    }
}
