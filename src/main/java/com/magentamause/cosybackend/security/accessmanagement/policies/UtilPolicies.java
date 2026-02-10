package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import java.util.Optional;

public class UtilPolicies {
    public static boolean IS_GAMESERVER_OWNER(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        Optional<GameServerEntity> gameServerEntity =
                resourceResolver.getGameServerEntity((String) referenceId);
        return gameServerEntity.isPresent() && IS_GAMESERVER_OWNER(gameServerEntity.get(), user);
    }

    public static boolean IS_GAMESERVER_OWNER(GameServerEntity gameServerEntity, UserEntity user) {
        return gameServerEntity.getOwner().equals(user);
    }
}
