package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import org.springframework.stereotype.Component;

@Component
public class GameServerWebhookPolicy {

    @Validates(Operation.GAME_SERVER_WEBHOOK_READ)
    public static boolean canReadWebhooks(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.READ_SERVER_WEBHOOKS);
    }

    @Validates(Operation.GAME_SERVER_WEBHOOK_UPDATE)
    public static boolean canUpdateWebhooks(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.CHANGE_SERVER_WEBHOOKS);
    }

    private static boolean checkPermission(
            ResourceResolver resolver,
            Object referenceId,
            UserEntity user,
            GameServerAccessPermission permission) {
        return resolver.getGameServerEntity((String) referenceId)
                .map(
                        server ->
                                GameServerPermissionsUtility.isOwnerOrHasPermission(
                                        server, user, permission))
                .orElse(false);
    }
}
