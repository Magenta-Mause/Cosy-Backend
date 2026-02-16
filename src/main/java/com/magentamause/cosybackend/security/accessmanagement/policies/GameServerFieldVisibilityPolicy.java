package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import java.util.List;

public class GameServerFieldVisibilityPolicy {

    // Permission-list-based (used by toDto(permissions) — efficient, extract once)

    public static boolean canSeeServerConfigs(List<GameServerAccessPermission> permissions) {
        return GameServerPermissionsUtility.can(
                GameServerAccessPermission.CHANGE_SERVER_CONFIGS, permissions);
    }

    public static boolean canSeeRconConfig(List<GameServerAccessPermission> permissions) {
        return GameServerPermissionsUtility.can(
                GameServerAccessPermission.CHANGE_RCON_SETTINGS, permissions);
    }

    public static boolean canSeeMetricLayout(List<GameServerAccessPermission> permissions) {
        return GameServerPermissionsUtility.can(
                        GameServerAccessPermission.CHANGE_METRICS_SETTINGS, permissions)
                || GameServerPermissionsUtility.can(
                        GameServerAccessPermission.READ_SERVER_METRICS, permissions);
    }

    public static boolean canSeeAccessGroups(List<GameServerAccessPermission> permissions) {
        return GameServerPermissionsUtility.can(
                GameServerAccessPermission.CHANGE_PERMISSIONS_SETTINGS, permissions);
    }

    // Resolves permissions from entity+user (used by toDto(user))

    public static List<GameServerAccessPermission> resolvePermissions(
            GameServerEntity server, UserEntity user) {
        if (user.getRole().isAdmin() || GameServerPermissionsUtility.isOwner(server, user)) {
            return List.of(GameServerAccessPermission.ADMIN);
        }
        return GameServerPermissionsUtility.extractUserPermissions(
                user.getUuid(), server.getAccessGroups());
    }
}
