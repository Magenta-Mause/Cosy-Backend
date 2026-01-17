package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class GameServerMetricPolicy implements AccessPolicy {
    private final GameServerRepository gameServerRepository;

    @Override
    public Resource resource() {
        return Resource.GAME_SERVER_METRIC;
    }

    @Override
    public boolean can(UserEntity user, Action action, Object referenceId) {
        if (user.getRole().isAdmin()) {
            return true;
        }

        GameServerEntity gameServerEntity =
                gameServerRepository
                        .findById((String) referenceId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Game server with uuid "
                                                        + referenceId
                                                        + " not found"));

        if (action == Action.READ) {
            return gameServerEntity.getOwner().getUuid().equals(user.getUuid());
        }
        throw new IllegalStateException("Unexpected value: " + action);
    }
}
