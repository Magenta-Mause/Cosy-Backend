package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.dtos.actiondtos.gameserver.GameServerUpdateDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupCreationDto;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration.AccessGroupUpdateDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.RCONConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.repositories.GameServerAccessGroupRepository;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.services.user.UserEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameServerConfigurationService {

    private final GameServerRepository gameServerRepository;
    private final GameServerService gameServerService;
    private final GameServerAccessGroupRepository gameServerAccessGroupRepository;
    private final UserEntityService userEntityService;

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

    public GameServerAccessGroup createAccessGroup(String gameServerUuid, AccessGroupCreationDto accessGroupCreationDto) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        GameServerAccessGroup accessGroup = new GameServerAccessGroup();
        accessGroup.setGameServer(gameServer);
        accessGroup.setUsers(List.of());
        accessGroup.setPermissions(List.of());
        return gameServerAccessGroupRepository.save(accessGroup);
    }

    public List<GameServerAccessGroup> updateAccessGroup(String gameServerUuid, String accessGroupUuid, AccessGroupUpdateDto updateDto) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        List<GameServerAccessGroup> accessGroups = gameServer.getAccessGroups();
        GameServerAccessGroup accessGroupToUpdate = getAccessGroup(accessGroupUuid);
        if (
                accessGroupToUpdate.getGameServer() == null
                        || !accessGroupToUpdate.getGameServer().getUuid().equals(gameServerUuid)
                        || accessGroups.stream().noneMatch(g -> g.getUuid().equals(accessGroupUuid))
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access group '" + accessGroupUuid + "' is not assigned to server '" + gameServerUuid + "'");
        }
        GameServerAccessGroup updatedAccessGroup = updateDto.applyOnEntity(accessGroupToUpdate, userEntityService::getUserByUsername);
        gameServerAccessGroupRepository.save(updatedAccessGroup);
        return gameServerService.getOrThrow(gameServerUuid).getAccessGroups();
    }

    public void deleteAccessGroup(String gameServerUuid, String accessGroupUuid) {
        GameServerEntity gameServer = gameServerService.getOrThrow(gameServerUuid);
        GameServerAccessGroup accessGroupToDelete = getAccessGroup(accessGroupUuid);
        if (accessGroupToDelete.getGameServer() == null || !accessGroupToDelete.getGameServer().getUuid().equals(gameServerUuid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access group '" + accessGroupUuid + "' is not assigned to server '" + gameServerUuid + "'");
        }
        gameServer.getAccessGroups().remove(accessGroupToDelete);
        gameServerRepository.save(gameServer);
    }

    private GameServerAccessGroup getAccessGroup(String accessGroupUuid) {
        return gameServerAccessGroupRepository.findById(accessGroupUuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access group '" + accessGroupUuid + "' not found"));
    }
}
