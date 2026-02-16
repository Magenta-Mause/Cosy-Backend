package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import org.springframework.stereotype.Component;

@Component
public class GameServerFilesPolicy {

    @Validates(Operation.GAME_SERVER_FILES_READ)
    public static boolean canReadFiles(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user,
                GameServerAccessPermission.READ_SERVER_SERVER_FILES);
    }

    @Validates(Operation.GAME_SERVER_FILES_UPDATE)
    public static boolean canUpdateFiles(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.CHANGE_SERVER_FILES);
    }

    private static boolean checkPermission(
            ResourceResolver resolver,
            Object referenceId,
            UserEntity user,
            GameServerAccessPermission permission) {
        return resolver.getGameServerEntity((String) referenceId)
                .map(server -> GameServerPermissionsUtility.isOwnerOrHasPermission(
                        server, user, permission))
                .orElse(false);
    }
}
