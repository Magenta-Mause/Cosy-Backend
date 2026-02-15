package com.magentamause.cosybackend.dtos.websockets;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.gameserver.utility.accessmanagement.GameServerAccessPermission;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerPermissionChangeDto {
    private List<GameServerAccessPermission> permissions;
    private String gameServerUuid;
}
