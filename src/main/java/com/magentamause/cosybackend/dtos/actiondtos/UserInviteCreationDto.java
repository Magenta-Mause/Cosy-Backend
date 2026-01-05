package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.ValidUsername;
import com.magentamause.cosybackend.entities.UserEntity;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInviteCreationDto {
    @ValidUsername private String username;
    private UserEntity.Role role;
    @Positive private Long maxMemory;
    @Positive private Long maxCpu;
}
