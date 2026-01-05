package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.uniqueElements.UniqueElementsBy;
import com.magentamause.cosybackend.entities.GameServerConfigurationEntity;
import com.magentamause.cosybackend.entities.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.utility.PortMapping;
import com.magentamause.cosybackend.entities.utility.VolumeMountConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerCreationDto {
    @NotBlank private String gameUuid;
    @NotBlank private String serverName;
    @NotBlank private String template;
    @NotBlank private String dockerImageName;
    @NotBlank private String dockerImageTag;

    @UniqueElementsBy(
            fieldNames = {"instancePort", "containerPort"},
            message = "duplicate port mapping")
    @Valid
    private List<PortMapping> portMappings;

    private List<@NotBlank String> executionCommand;

    @UniqueElementsBy(
            fieldNames = {"key", "value"},
            message = "duplicate environment variable")
    @Valid
    private List<EnvironmentVariableConfiguration> environmentVariables;

    @UniqueElementsBy(
            fieldNames = {"hostPath", "containerPath"},
            message = "duplicate volume mounts")
    @Valid
    private List<VolumeMountConfigurationCreationDto> volumeMounts;

    public GameServerConfigurationEntity toEntity() {
        return GameServerConfigurationEntity.builder()
                .gameUuid(this.getGameUuid())
                .serverName(this.getServerName())
                .template(this.getTemplate())
                .dockerImageName(this.getDockerImageName())
                .dockerImageTag(this.getDockerImageTag())
                .dockerExecutionCommand(this.getExecutionCommand())
                .environmentVariables(this.getEnvironmentVariables())
                .volumeMounts(
                        this.getVolumeMounts() != null
                                ? this.getVolumeMounts().stream()
                                        .map(VolumeMountConfiguration::fromDto)
                                        .toList()
                                : null)
                .portMappings(this.getPortMappings() != null ? this.getPortMappings() : List.of())
                .build();
    }
}
