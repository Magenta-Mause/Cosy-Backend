package com.magentamause.cosybackend.dtos.entitydtos;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.DockerHardwareLimits;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserEntityDto {
    private String uuid;
    private String username;
    private UserEntity.Role role;
    private DockerHardwareLimits dockerHardwareLimits;
}
