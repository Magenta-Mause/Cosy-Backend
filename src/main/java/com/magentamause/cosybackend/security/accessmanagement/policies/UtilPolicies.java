package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UtilPolicies {
    public static boolean IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
            ResourceResolver resourceResolver,
            Object referenceId,
            UserEntity user,
            GameServerAccessPermission permission) {
        try {

            Optional<GameServerEntity> gameServerEntity =
                    resourceResolver.getGameServerEntity((String) referenceId);
            if (gameServerEntity.isEmpty()) {
                return false;
            }
            return GameServerPermissionsUtility.isOwnerOrHasPermission(
                    gameServerEntity.get(), user, permission);
        } catch (Exception e) {
            log.error("Error checking permissions", e);
            throw new RuntimeException(e);
        }
    }

    public static boolean IS_GAMESERVER_OWNER(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        Optional<GameServerEntity> gameServerEntity =
                resourceResolver.getGameServerEntity((String) referenceId);
        return gameServerEntity.isPresent()
                && GameServerPermissionsUtility.isOwner(gameServerEntity.get(), user);
    }
}
