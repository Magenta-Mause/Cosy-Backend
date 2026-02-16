package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import org.springframework.stereotype.Component;

@Component
public class GameServerPolicy {

    @Validates(Operation.GAME_SERVER_CREATE)
    public static boolean canCreateGameServer(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return true;
    }

    @Validates(Operation.GAME_SERVER_DELETE)
    public static boolean canDeleteGameServer(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.DELETE_SERVER);
    }

    @Validates(Operation.GAME_SERVER_START_STOP)
    public static boolean canStartStopGameServer(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.START_STOP_SERVER);
    }

    @Validates(Operation.GAME_SERVER_UPDATE)
    public static boolean canUpdateGameServer(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.CHANGE_SERVER_CONFIGS);
    }

    @Validates(Operation.GAME_SERVER_GET)
    public static boolean canGetGameServer(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(resolver, referenceId, user, GameServerAccessPermission.SEE_SERVER);
    }

    @Validates(Operation.GAME_SERVER_GET_ALL)
    public static boolean canGetAllGameServers(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return false;
    }

    @Validates(Operation.GAME_SERVER_SEND_COMMAND)
    public static boolean canSendCommand(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.SEND_COMMANDS);
    }

    @Validates(Operation.GAME_SERVER_GET_LOGS)
    public static boolean canGetLogs(
            ResourceResolver resolver, Object referenceId, UserEntity user) {
        return checkPermission(
                resolver, referenceId, user, GameServerAccessPermission.READ_SERVER_LOGS);
    }

    private static boolean checkPermission(
            ResourceResolver resolver,
            Object referenceId,
            UserEntity user,
            GameServerAccessPermission permission) {
        return resolver.getGameServerEntity((String) referenceId)
                .map(
                        server ->
                                GameServerPermissionsUtility.isOwnerOrHasPermission(
                                        server, user, permission))
                .orElse(false);
    }
}
