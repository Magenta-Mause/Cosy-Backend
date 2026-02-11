package com.magentamause.cosybackend.security.accessmanagement.policies;

import static com.magentamause.cosybackend.security.accessmanagement.policies.UtilPolicies.IS_GAMESERVER_OWNER_OR_HAS_PERMISSION;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

@Component
public class GameServerPolicy {

    @Validates(Operation.GAME_SERVER_CREATE)
    public boolean createGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return true;
    }

    @Validates(Operation.GAME_SERVER_DELETE)
    public boolean deleteGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver, referenceId, user, GameServerAccessPermission.DELETE_SERVER);
    }

    @Validates(Operation.GAME_SERVER_START_STOP)
    public boolean startStopGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver, referenceId, user, GameServerAccessPermission.START_STOP_SERVER);
    }

    @Validates(Operation.GAME_SERVER_UPDATE)
    public boolean updateGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver,
                referenceId,
                user,
                GameServerAccessPermission.CHANGE_SERVER_CONFIGS);
    }

    @Validates(Operation.GAME_SERVER_GET)
    public boolean getGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        // TODO: As soon as public dashboard configuration is being done, add check here:
        // TODO: gameServer.publicDashboardConfiguration.enabled = true -> true
        return IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver, referenceId, user, GameServerAccessPermission.SEE_SERVER);
    }

    @Validates(Operation.GAME_SERVER_GET_ALL)
    public boolean getAllGameServers(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return false;
    }

    @Validates(Operation.GAME_SERVER_SEND_COMMAND)
    public boolean sendCommandToGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver, referenceId, user, GameServerAccessPermission.SEND_COMMANDS);
    }

    @Validates(Operation.GAME_SERVER_GET_LOGS)
    public boolean getGameServerLogs(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER_OR_HAS_PERMISSION(
                resourceResolver, referenceId, user, GameServerAccessPermission.READ_SERVER_LOGS);
    }
}
