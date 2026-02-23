package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

@Component
public class FooterPolicy {

    @Validates(Operation.FOOTER_UPDATE)
    public static boolean canUpdateFooter(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return UserEntity.Role.OWNER.equals(user.getRole());
    }
}
