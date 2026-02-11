package com.magentamause.cosybackend.services.auth;

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
}
