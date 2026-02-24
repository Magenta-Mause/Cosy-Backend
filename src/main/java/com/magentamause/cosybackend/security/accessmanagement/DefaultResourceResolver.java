package com.magentamause.cosybackend.security.accessmanagement;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.services.core.gameserver.GameServerService;
import com.magentamause.cosybackend.services.user.UserEntityService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultResourceResolver implements ResourceResolver {

    private final GameServerService gameServerService;
    private final UserEntityService userEntityService;

    @Override
    public Optional<GameServerEntity> getGameServerEntity(String gameServerUuid) {
        return gameServerService.getOptionalGameServerById(gameServerUuid);
    }

    @Override
    public Optional<UserEntity> getUserEntity(String userUuid) {
        return userEntityService.getOptionalUserByUuid(userUuid);
    }
}
