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
public class UserPortRestrictionsUpdateDto {

    private Boolean portRestrictionsEnabled;
    private List<String> allowedPorts;

    public UserEntity applyToEntity(UserEntity entity) {
        if (this.portRestrictionsEnabled != null) {
            entity.setPortRestrictionsEnabled(this.portRestrictionsEnabled);
        }
        if (this.allowedPorts != null) {
            entity.setAllowedPorts(this.allowedPorts);
        }
        return entity;
    }
}
