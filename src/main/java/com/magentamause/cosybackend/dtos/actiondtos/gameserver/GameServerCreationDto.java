package com.magentamause.cosybackend.dtos.actiondtos.gameserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.uniqueElements.UniqueElementsBy;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.entities.gameserver.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.GameServerDesign;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameServerCreationDto {
    private Integer externalGameId;
    private GameServerDesign design;
    @NotBlank private String serverName;
    @NotBlank private String dockerImageName;
    @NotBlank private String dockerImageTag;

    @Valid private DockerHardwareLimits dockerHardwareLimits;

    @Valid private List<PortMapping> portMappings;

    private List<@NotBlank String> executionCommand;

    @UniqueElementsBy(
            fieldNames = {"key", "value"},
            message = "duplicate environment variable")
    @Valid
    private List<EnvironmentVariableConfiguration> environmentVariables;

    @UniqueElementsBy(
            fieldNames = {"containerPath"},
            message = "duplicate volume mounts")
    @Valid
    private List<VolumeMountConfigurationDto> volumeMounts;

    private List<@NotBlank String> mcRouterDomains;

    public GameServerEntity toEntity(UserEntity user, Function<Integer, GameEntity> gameProvider) {

        return GameServerEntity.builder()
                .game(gameProvider.apply(this.externalGameId))
                .owner(user)
                .design(this.getDesign())
                .serverName(this.getServerName())
                .dockerImageName(this.getDockerImageName())
                .dockerImageTag(this.getDockerImageTag())
                .dockerExecutionCommand(this.getExecutionCommand())
                .dockerHardwareLimits(this.getDockerHardwareLimits())
                .environmentVariables(this.getEnvironmentVariables())
                .volumeMounts(
                        this.getVolumeMounts() != null
                                ? this.getVolumeMounts().stream()
                                        .map(VolumeMountConfiguration::fromDto)
                                        .toList()
                                : List.of())
                .portMappings(this.getPortMappings() != null ? this.getPortMappings() : List.of())
                .mcRouterDomains(
                        this.getMcRouterDomains() != null
                                ? new ArrayList<>(this.getMcRouterDomains())
                                : List.of())
                .build();
    }
}
