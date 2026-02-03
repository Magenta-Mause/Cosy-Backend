package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.uniqueElements.UniqueElementsBy;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.utility.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerUpdateDto {
    private Integer externalGameId;
    @NotBlank private String serverName;
    @NotBlank private String dockerImageName;
    @NotBlank private String dockerImageTag;

    private RCONConfiguration rconConfiguration;

    @Valid private DockerHardwareLimits dockerHardwareLimits;

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

    public void applyToEntity(GameServerEntity target, Function<Integer, GameEntity> gameProvider) {
        target.setGame(gameProvider.apply(this.externalGameId));
        target.setServerName(this.getServerName());
        target.setDockerImageName(this.getDockerImageName());
        target.setDockerImageTag(this.getDockerImageTag());
        target.setDockerExecutionCommand(this.getExecutionCommand());
        target.setRconConfiguration(this.getRconConfiguration());
        target.setDockerHardwareLimits(this.getDockerHardwareLimits());

        target.setPortMappings(
                updateList(target.getPortMappings(), this.getPortMappings(), ArrayList::new));
        target.setEnvironmentVariables(
                updateList(
                        target.getEnvironmentVariables(),
                        this.getEnvironmentVariables(),
                        ArrayList::new));
        target.setVolumeMounts(
                updateList(
                        target.getVolumeMounts(),
                        this.getVolumeMounts() != null
                                ? this.getVolumeMounts().stream()
                                        .map(VolumeMountConfiguration::fromDto)
                                        .toList()
                                : null,
                        ArrayList::new));
    }

    private <T> List<T> updateList(List<T> target, List<T> source, Supplier<List<T>> listSupplier) {
        if (target == null) {
            target = listSupplier.get();
        } else {
            target.clear();
        }
        if (source != null) {
            target.addAll(source);
        }
        return target;
    }
}
