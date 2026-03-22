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
public class UserMcRouterRestrictionsUpdateDto {

    private Boolean mcRouterAllowAllDomains;
    private List<String> mcRouterAllowedDomains;

    public UserEntity applyToEntity(UserEntity entity) {
        if (this.mcRouterAllowAllDomains != null) {
            entity.setMcRouterAllowAllDomains(this.mcRouterAllowAllDomains);
        }
        if (this.mcRouterAllowedDomains != null) {
            entity.setMcRouterAllowedDomains(this.mcRouterAllowedDomains);
        }
        return entity;
    }
}
