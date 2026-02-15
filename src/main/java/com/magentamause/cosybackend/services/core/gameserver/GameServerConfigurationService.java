package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.repositories.GameServerAccessGroupRepository;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.auth.GameServerPermissionsUtility;
import com.magentamause.cosybackend.services.user.UserEntityService;
import com.magentamause.cosybackend.websockets.UserPermissionsPublisher;

import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GameServerConfigurationService {

    private final GameServerRepository gameServerRepository;
    private final GameServerService gameServerService;
    private final GameServerAccessGroupRepository gameServerAccessGroupRepository;
    private final UserEntityService userEntityService;
    private final UserPermissionsPublisher userPermissionsPublisher;

    public GameServerEntity updateRconConfig(String uuid, RCONConfiguration updateDto) {
        GameServerEntity gameServer = gameServerService.getOrThrow(uuid);
        gameServer.setRconConfiguration(updateDto);
        return gameServerService.saveGameServerConfiguration(gameServer, false);
    }

    public void updateMetricLayout(String gameServerUuid, List<MetricLayout> metricLayout) {
        GameServerEntity gameServer =
                gameServerRepository
                        .findById(gameServerUuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Server '" + gameServerUuid + "' not found"));

        gameServer.getMetricLayout().clear();
        gameServer.getMetricLayout().addAll(metricLayout);
        gameServerRepository.save(gameServer);
    }

    public GameServerAccessGroup createAccessGroup(
            String gameServerUuid, AccessGroupCreationDto accessGroupCreationDto) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        GameServerAccessGroup accessGroup = new GameServerAccessGroup();
        accessGroup.setGroupName(accessGroupCreationDto.getName());
        accessGroup.setGameServer(gameServer);
        accessGroup.setUsers(List.of());
        accessGroup.setPermissions(List.of());
        return gameServerAccessGroupRepository.save(accessGroup);
    }

    public List<GameServerAccessGroup> updateAccessGroup(
            String gameServerUuid, String accessGroupUuid, AccessGroupUpdateDto updateDto) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        List<GameServerAccessGroup> accessGroups = gameServer.getAccessGroups() != null 
                ? gameServer.getAccessGroups() 
                : List.of();
        GameServerAccessGroup accessGroupToUpdate = getAccessGroup(accessGroupUuid);
        if (accessGroupToUpdate.getGameServer() == null
                || !accessGroupToUpdate.getGameServer().getUuid().equals(gameServerUuid)
                || accessGroups.stream().noneMatch(g -> g.getUuid().equals(accessGroupUuid))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Access group '"
                            + accessGroupUuid
                            + "' is not assigned to server '"
                            + gameServerUuid
                            + "'");
        }
        HashSet<UserEntity> usersToNotify = new HashSet<>(accessGroupToUpdate.getUsers());

        GameServerAccessGroup updatedAccessGroup =
                updateDto.applyOnEntity(accessGroupToUpdate, userEntityService::getUserByUuid);
        usersToNotify.addAll(updatedAccessGroup.getUsers());
        gameServerAccessGroupRepository.save(updatedAccessGroup);
        sendPermissionUpdateNotification(usersToNotify.stream().toList(), gameServerUuid);
        GameServerEntity updatedGameServer = gameServerService.getOrThrow(gameServerUuid);
        return updatedGameServer.getAccessGroups() != null 
                ? updatedGameServer.getAccessGroups() 
                : List.of();
    }

    public void sendPermissionUpdateNotification(List<UserEntity> users, String serverId) {
        users.forEach(
                user ->
                        userPermissionsPublisher.publishPermissionUpdate(
                                user.getUuid(),
                                serverId,
                                getUserPermissions(serverId, user.getUuid())));
    }

    public void deleteAccessGroup(String gameServerUuid, String accessGroupUuid) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        GameServerAccessGroup accessGroupToDelete = getAccessGroup(accessGroupUuid);
        if (accessGroupToDelete.getGameServer() == null
                || !accessGroupToDelete.getGameServer().getUuid().equals(gameServerUuid)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Access group '"
                            + accessGroupUuid
                            + "' is not assigned to server '"
                            + gameServerUuid
                            + "'");
        }
        List<UserEntity> usersToNotify = new ArrayList<>(accessGroupToDelete.getUsers());
        if (gameServer.getAccessGroups() != null) {
            gameServer.getAccessGroups().remove(accessGroupToDelete);
        }
        gameServerRepository.save(gameServer);
        sendPermissionUpdateNotification(usersToNotify, gameServerUuid);
    }

    private GameServerAccessGroup getAccessGroup(String accessGroupUuid) {
        return gameServerAccessGroupRepository
                .findById(accessGroupUuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Access group '" + accessGroupUuid + "' not found"));
    }

    public List<GameServerAccessPermission> getUserPermissions(
            GameServerEntity gameServer, String userUuid) {
        if (gameServer.getOwner().getUuid().equals(userUuid)) {
            return List.of(GameServerAccessPermission.ADMIN);
        }

        UserEntity user = userEntityService.getOptionalUserByUuid(userUuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole().isAdmin()) {
            return List.of(GameServerAccessPermission.ADMIN);
        }

        return GameServerPermissionsUtility.extractUserPermissions(
                userUuid, gameServer.getAccessGroups() != null 
                        ? gameServer.getAccessGroups() 
                        : List.of());
    }

    public List<GameServerAccessPermission> getUserPermissions(
            String gameServerUuid, String userUuid) {
        Optional<GameServerEntity> gameServerOptional =
                gameServerService.getGameServerOptionalById(gameServerUuid);
        if (gameServerOptional.isEmpty()) {
            return List.of();
        }
        return getUserPermissions(gameServerOptional.get(), userUuid);
    }
}
