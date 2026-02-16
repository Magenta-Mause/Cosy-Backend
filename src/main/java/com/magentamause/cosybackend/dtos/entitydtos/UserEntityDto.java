package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
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
}
