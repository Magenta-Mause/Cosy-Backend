package com.magentamause.cosybackend.dtos.actiondtos.user;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.UserEntity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserRestrictionsUpdateDto {

    // Port restrictions
    private boolean portRestrictionsEnabled;
    private List<String> allowedPorts;

    // Game server creation permission
    private boolean allowGameServerCreation;

    // MC-Router domain restrictions
    private boolean mcRouterAllowAllDomains;
    private List<String> mcRouterAllowedDomains;

    public UserEntity applyToEntity(UserEntity entity) {
        entity.setPortRestrictionsEnabled(this.portRestrictionsEnabled);
        entity.setAllowedPorts(this.allowedPorts);
        entity.setAllowGameServerCreation(this.allowGameServerCreation);
        entity.setMcRouterAllowAllDomains(this.mcRouterAllowAllDomains);
        entity.setMcRouterAllowedDomains(this.mcRouterAllowedDomains);
        return entity;
    }
}
