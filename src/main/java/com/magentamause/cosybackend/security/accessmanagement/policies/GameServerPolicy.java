package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.security.accessmanagement.ResourceResolver;
import com.magentamause.cosybackend.security.accessmanagement.Validates;
import org.springframework.stereotype.Component;

import static com.magentamause.cosybackend.security.accessmanagement.policies.UtilPolicies.IS_GAMESERVER_OWNER;

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
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }

    @Validates(Operation.GAME_SERVER_START_STOP)
    public boolean startStopGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }

    @Validates(Operation.GAME_SERVER_UPDATE)
    public boolean updateGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }

    @Validates(Operation.GAME_SERVER_GET)
    public boolean getGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        // TODO: As soon as public dashboard configuration is being done, add check here:
        // gameServer.publicDashboardConfiguration.enabled = true -> true
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }

    @Validates(Operation.GAME_SERVER_GET_ALL)
    public boolean getAllGameServers(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return false;
    }

    @Validates(Operation.GAME_SERVER_SEND_COMMAND)
    public boolean sendCommandToGameServer(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }

    @Validates(Operation.GAME_SERVER_GET_LOGS)
    public boolean getGameServerLogs(
            ResourceResolver resourceResolver, Object referenceId, UserEntity user) {
        return IS_GAMESERVER_OWNER(resourceResolver, referenceId, user);
    }

}
