package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;

import java.util.List;
import java.util.Optional;

public class UtilPolicies {
    public static boolean IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(ResourceResolver resourceResolver, Object referenceId, UserEntity user, GameServerAccessPermission permission) {
        Optional<GameServerEntity> gameServerEntity = resourceResolver.getGameServerEntity((String) referenceId);
        if (gameServerEntity.isEmpty()) {
            return false;
        }
        if (IS_GAMESERVER_OWNER(gameServerEntity.get(), user)) {
            return true;
        }
        List<GameServerAccessGroup> accessGroups = gameServerEntity.get().getAccessGroups();
        List<GameServerAccessPermission> userPermissions = GameServerPermissionsUtility.extractUserPermissions(user.getUuid(), accessGroups);
        return userPermissions.contains(permission);
    }

    public static boolean IS_GAMESERVER_OWNER(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        Optional<GameServerEntity> gameServerEntity =
                resourceResolver.getGameServerEntity((String) referenceId);
        return gameServerEntity.isPresent() && IS_GAMESERVER_OWNER(gameServerEntity.get(), user);
    }

    public static boolean IS_GAMESERVER_OWNER(GameServerEntity gameServerEntity, UserEntity user) {
        return gameServerEntity.getOwner().getUuid().equals(user.getUuid());
    }
}
