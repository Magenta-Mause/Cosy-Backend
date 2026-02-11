package com.magentamause.cosybackend.security.accessmanagement.policies;

import static com.magentamause.cosybackend.security.accessmanagement.policies.UtilPolicies.IS_GAMESERVER_OWNER;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameServerMetricPolicy {

    @Validates(Operation.GAME_SERVER_METRIC_READ)
    public boolean getGameServerMetrics(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        // TODO: For metrics we should not just expose every metric but check which metrics are
        // TODO: configured to be exposed publicly and add a check here for the game server and for
        // TODO: the
        // TODO: metric
        // TODO: Hint for implementation: change the @ResourceId annotation to allow for more then
        // TODO: one parameter of a methode to be annotated with @ResourceId, then the ReferenceId
        // TODO: passed
        // TODO: into these policy methods should be an array of x entries where each entry is one
        // TODO: ResourceId passed into the method
        return UtilPolicies.IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(resourceResolver, referenceId, user, GameServerAccessPermission.READ_SERVER_METRICS);
    }
}
