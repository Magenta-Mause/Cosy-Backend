package com.magentamause.cosybackend.dtos.actiondtos.gameserver.configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessGroupEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessGroupUpdateDto {
    @NotEmpty private String accessGroupName;
    private List<String> userUuids;
    private List<GameServerAccessPermission> permissions;

    public GameServerAccessGroupEntity applyOnEntity(
            GameServerAccessGroupEntity gameServerAccessGroup,
            Function<String, UserEntity> userResolver) {
        gameServerAccessGroup.setGroupName(accessGroupName);
        if (userUuids != null) {
            gameServerAccessGroup.setUsers(
                    new ArrayList<>(userUuids.stream().map(userResolver).toList()));
        }
        gameServerAccessGroup.setPermissions(
                permissions == null ? new ArrayList<>() : new ArrayList<>(permissions));
        return gameServerAccessGroup;
    }
}
