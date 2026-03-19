package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

@Component
public class CosyInstanceSettingsPolicy {

    @Validates(Operation.COSY_SETTINGS_READ)
    public static boolean canReadSettings(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user != null && user.getRole().isAdmin();
    }

    @Validates(Operation.COSY_SETTINGS_UPDATE)
    public static boolean canUpdateSettings(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user != null && user.getRole().isAdmin();
    }

    @Validates(Operation.MC_ROUTER_STATUS_READ)
    public static boolean canReadMcRouterStatus(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return user != null && user.getRole().isAdmin();
    }
}
