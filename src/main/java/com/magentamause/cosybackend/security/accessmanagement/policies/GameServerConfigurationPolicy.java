package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import org.springframework.stereotype.Component;

@Component
public class GameServerConfigurationPolicy {

    @Validates(Operation.GAME_SERVER_METRIC_CONFIG_CHANGE)
    public static boolean canChangeMetricsConfig(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.CHANGE_METRICS_SETTINGS);
    }

    @Validates(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public static boolean canChangePermissionsConfig(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user,
                GameServerAccessPermission.CHANGE_PERMISSIONS_SETTINGS);
    }

    @Validates(Operation.GAME_SERVER_RCON_CONFIG_CHANGE)
    public static boolean canChangeRconConfig(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.CHANGE_RCON_SETTINGS);
    }

    private static boolean checkPermission(
            ResourceResolver resolver,
            Object referenceId,
            UserEntity user,
            GameServerAccessPermission permission) {
        return resolver.getGameServerEntity((String) referenceId)
                .map(server -> GameServerPermissionsUtility.isOwnerOrHasPermission(
                        server, user, permission))
                .orElse(false);
    }
}
