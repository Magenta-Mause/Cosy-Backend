package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserEntityDto {
    private String uuid;
    private String username;
    private UserEntity.Role role;
    private DockerHardwareLimits dockerHardwareLimits;

    // Port restrictions
    private boolean portRestrictionsEnabled;
    private List<String> allowedPorts;

    // Game server creation permission
    private boolean allowGameServerCreation;

    // MC-Router domain restrictions
    private boolean mcRouterAllowAllDomains;
    private List<String> mcRouterAllowedDomains;
}
