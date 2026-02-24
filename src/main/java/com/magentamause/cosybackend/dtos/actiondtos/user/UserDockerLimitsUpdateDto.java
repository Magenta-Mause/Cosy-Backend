package com.magentamause.cosybackend.dtos.actiondtos.user;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDockerLimitsUpdateDto {
    @NotNull @Valid private DockerHardwareLimits dockerHardwareLimits;
}
