package com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import lombok.Data;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.function.Function;

@Data
@JsonNaming(tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessGroupUpdateDto {
    private String accessGroupName;
    private List<String> userUuids;
    private List<GameServerAccessPermission> permissions;

    public GameServerAccessGroup applyOnEntity(GameServerAccessGroup gameServerAccessGroup, Function<String, UserEntity> userResolver) {
        gameServerAccessGroup.setGroupName(accessGroupName);
        gameServerAccessGroup.setUsers(userUuids.stream().map(userResolver::apply).toList());
        gameServerAccessGroup.setPermissions(permissions);
        return gameServerAccessGroup;
    }
}
