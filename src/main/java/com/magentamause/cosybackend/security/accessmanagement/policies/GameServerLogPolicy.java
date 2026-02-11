package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GameServerLogPolicy {

    @Validates(Operation.GAME_SERVER_LOG_READ)
    public boolean readGameServerLogs(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return UtilPolicies.IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver, referenceId, user, GameServerAccessPermission.READ_SERVER_LOGS);
    }
}
