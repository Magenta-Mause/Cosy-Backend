package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.services.GameServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameServerPolicy implements AccessPolicy {

    private final GameServerService gameServerService;

    @Override
    public Resource resource() {
        return Resource.GAME_SERVER;
    }

    @Override
    public boolean can(UserEntity user, Action action, Object referenceId) {
        if (user.getRole().isAdmin()) {
            return true;
        }

        if (action == Action.CREATE) {
            return true;
        }

        if (!(referenceId instanceof String)) {
            return action == Action.READ;
        }

        GameServerEntity gameServerEntity =
                gameServerService.getGameServerById((String) referenceId);

        return switch (action) {
            case READ, DELETE, UPDATE -> gameServerEntity
                    .getOwner()
                    .getUuid()
                    .equals(user.getUuid());
            default -> throw new IllegalStateException("Unexpected value: " + action);
        };
    }
}
