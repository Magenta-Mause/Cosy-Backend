package com.magentamause.cosybackend.security.accessmanagement.policies;

import static com.magentamause.cosybackend.security.accessmanagement.policies.UtilPolicies.IS_GAMESERVER_OWNER;

import com.magentamause.cosybackend.entities.UserEntity;
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
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }
}
