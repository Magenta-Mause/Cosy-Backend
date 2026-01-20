package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.ValidUsername;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.DockerHardwareLimits;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInviteCreationDto {
    @ValidUsername private String username;
    private UserEntity.Role role;

    private DockerHardwareLimits dockerHardwareLimits;
}
