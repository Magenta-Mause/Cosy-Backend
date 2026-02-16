package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import org.springframework.stereotype.Component;

@Component
public class GameServerMetricPolicy {

    @Validates(Operation.GAME_SERVER_METRIC_READ)
    public static boolean canReadMetrics(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return resolver.getGameServerEntity((String) referenceId)
                .map(
                        server ->
                                GameServerPermissionsUtility.isOwnerOrHasPermission(
                                        server,
                                        user,
                                        GameServerAccessPermission.READ_SERVER_METRICS))
                .orElse(false);
    }
}
