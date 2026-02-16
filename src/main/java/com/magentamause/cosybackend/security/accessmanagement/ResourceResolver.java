package com.magentamause.cosybackend.security.accessmanagement;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import java.util.Optional;

public interface ResourceResolver {

    Optional<GameServerEntity> getGameServerEntity(String gameServerUuid);

    Optional<UserEntity> getUserEntity(String userUuid);

    static ResourceResolver of(GameServerEntity gameServer) {
        return new ResourceResolver() {
            @Override
            public Optional<GameServerEntity> getGameServerEntity(String gameServerUuid) {
                return Optional.ofNullable(gameServer);
            }

            @Override
            public Optional<UserEntity> getUserEntity(String userUuid) {
                return Optional.empty();
            }
        };
    }
}
