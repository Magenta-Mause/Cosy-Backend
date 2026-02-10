package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

@Component
public class UserInvitePolicy {

    @Validates(Operation.USER_INVITE_CREATE)
    public boolean canCreateInvite(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        // every non-admin should not be able to create an invite - admins have permission to
        // override all policies anyways so no need to check here
        return false;
    }

    @Validates(Operation.USER_INVITE_READ)
    public boolean canReadInvite(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return false;
    }

    @Validates(Operation.USER_INVITE_DELETE)
    public boolean canDeleteInvite(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return false;
    }
}
