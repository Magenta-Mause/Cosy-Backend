package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

@Component
public class GameServerConfigurationPolicy {

    @Validates(Operation.GAME_SERVER_METRIC_CONFIG_CHANGE)
    public boolean gameServerMetricsConfigChange(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return UtilPolicies.IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver,
                referenceId,
                user,
                GameServerAccessPermission.CHANGE_METRICS_SETTINGS);
    }

    @Validates(Operation.GAME_SERVER_PERMISSIONS_CONFIG_CHANGE)
    public boolean gameServerPermissionsConfigChange(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return UtilPolicies.IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver,
                referenceId,
                user,
                GameServerAccessPermission.CHANGE_PERMISSIONS_SETTINGS);
    }

    @Validates(Operation.GAME_SERVER_RCON_CONFIG_CHANGE)
    public boolean gameServerRconConfigChange(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return UtilPolicies.IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver,
                referenceId,
                user,
                GameServerAccessPermission.CHANGE_RCON_SETTINGS);
    }
}
