package com.magentamause.cosybackend.services.auth;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;

import java.util.List;

public class GameServerPermissionsUtility {

    public static List<GameServerAccessPermission> extractUserPermissions(
            String userId, List<GameServerAccessGroup> accessGroups) {
        List<GameServerAccessGroup> accessGroupsOfUser =
                accessGroups.stream()
                        .filter(
                                gameServerAccessGroup ->
                                        gameServerAccessGroup.getUsers().stream()
                                                .anyMatch(
                                                        userEntity ->
                                                                userEntity
                                                                        .getUuid()
                                                                        .equals(userId)))
                        .toList();
        return accessGroupsOfUser.stream().flatMap(p -> p.getPermissions().stream()).toList();
    }

    public static boolean isOwner(GameServerEntity gameServer, UserEntity user) {
        return gameServer.getOwner().getUuid().equals(user.getUuid());
    }

    public static boolean isOwnerOrHasPermission(
            GameServerEntity gameServer, UserEntity user, GameServerAccessPermission permission) {
        if (user.getRole().isAdmin() || isOwner(gameServer, user)) {
            return true;
        }
        List<GameServerAccessPermission> userPermissions =
                extractUserPermissions(user.getUuid(), gameServer.getAccessGroups());
        return userPermissions.contains(GameServerAccessPermission.ADMIN)
                || userPermissions.contains(permission);
    }
}
