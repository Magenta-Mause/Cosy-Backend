package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerAccessGroupDto {
    @NotNull
    private String uuid;
    @NotNull
    private String groupName;
    @NotNull
    private List<GameServerAccessPermission> permissions;
    @NotNull
    private List<UserEntityDto> users;
    @NotNull
    private String gameServerUuid;
}
