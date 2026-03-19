package com.magentamause.cosybackend.dtos.actiondtos.user;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.ValidUsername;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInviteCreationDto {
    @ValidUsername private String username;
    private UserEntity.Role role;

    @Valid private DockerHardwareLimits dockerHardwareLimits;

    // Port restrictions
    private boolean portRestrictionsEnabled = false;
    private List<String> allowedPorts;

    // Game server creation permission
    private boolean allowGameServerCreation = true;

    // MC-Router domain restrictions
    private boolean mcRouterAllowAllDomains = false;
    private List<String> mcRouterAllowedDomains;
}
