package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class GameServerDto {
    @NotBlank private String uuid;
    @NotBlank private String serverName;

    @NotBlank @Valid private UserEntityDto owner;

    @NotNull @Valid private GameServerConfigurationEntity.GameServerStatus status;

    @NotNull @Valid private LocalDateTime timestampLastStarted;

    @NotBlank private String gameUuid;

    @NotBlank private String dockerImageName;

    @NotBlank private String dockerImageTag;

    private String template;

    private List<String> executionCommand;

    @NotNull @Valid private List<PortMapping> portMappings;

    @NotNull @Valid private List<EnvironmentVariableConfiguration> environmentVariables;

    @NotNull @Valid private List<VolumeMountConfiguration> volumeMounts;
}
