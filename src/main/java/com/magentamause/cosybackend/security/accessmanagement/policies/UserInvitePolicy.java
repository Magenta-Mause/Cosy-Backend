package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import org.springframework.stereotype.Component;

@Component
public class UserInvitePolicy implements AccessPolicy {

    @Override
    public Resource resource() {
        return Resource.USER_INVITE;
    }

    @Override
    public boolean can(UserEntity user, Action action, Object referenceId) {
        return user.getRole().isAdmin();
    }
}
