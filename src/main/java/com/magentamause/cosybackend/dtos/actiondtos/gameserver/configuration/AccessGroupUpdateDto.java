package com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroup;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessGroupUpdateDto {
    @NotEmpty
    private String accessGroupName;
    private List<String> userUuids;
    private List<GameServerAccessPermission> permissions;

    public GameServerAccessGroup applyOnEntity(
            GameServerAccessGroup gameServerAccessGroup,
            Function<String, UserEntity> userResolver) {
        gameServerAccessGroup.setGroupName(accessGroupName);
        if (userUuids != null) {
            gameServerAccessGroup.setUsers(new ArrayList<>(userUuids.stream().map(userResolver).toList()));
        }
        gameServerAccessGroup.setPermissions(permissions == null ? null : new ArrayList<>(permissions));
        return gameServerAccessGroup;
    }
}
