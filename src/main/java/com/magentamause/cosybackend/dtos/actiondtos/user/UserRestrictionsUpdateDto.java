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
    private Boolean portRestrictionsEnabled;
    private List<String> allowedPorts;

    // Game server creation permission
    private Boolean allowGameServerCreation;

    // MC-Router domain restrictions
    private Boolean mcRouterAllowAllDomains;
    private List<String> mcRouterAllowedDomains;

    public UserEntity applyToEntity(UserEntity entity) {
        if (this.portRestrictionsEnabled != null) {
            entity.setPortRestrictionsEnabled(this.portRestrictionsEnabled);
        }
        if (this.allowedPorts != null) {
            entity.setAllowedPorts(this.allowedPorts);
        }
        if (this.allowGameServerCreation != null) {
            entity.setAllowGameServerCreation(this.allowGameServerCreation);
        }
        if (this.mcRouterAllowAllDomains != null) {
            entity.setMcRouterAllowAllDomains(this.mcRouterAllowAllDomains);
        }
        if (this.mcRouterAllowedDomains != null) {
            entity.setMcRouterAllowedDomains(this.mcRouterAllowedDomains);
        }
        return entity;
    }
}
