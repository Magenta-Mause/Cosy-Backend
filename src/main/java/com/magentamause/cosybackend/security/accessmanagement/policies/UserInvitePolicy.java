package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

@Component
public class UserInvitePolicy {

    @Validates(Operation.USER_INVITE_CREATE)
    public static boolean canCreateInvite(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getRole().isAdmin();
    }

    @Validates(Operation.USER_INVITE_READ)
    public static boolean canReadInvite(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getRole().isAdmin();
    }

    @Validates(Operation.USER_INVITE_DELETE)
    public static boolean canDeleteInvite(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user.getRole().isAdmin();
    }
}
