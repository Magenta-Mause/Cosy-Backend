package com.magentamause.cosybackend.dtos.actiondtos.gameserver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.uniqueElements.UniqueElementsBy;
import com.magentamause.cosybackend.entities.GameEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.entities.gameserver.utility.EnvironmentVariableConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.HostVolumeMountConfiguration;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;
import com.magentamause.cosybackend.entities.gameserver.utility.VolumeMountConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerUpdateDto {
    private Integer externalGameId;
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

    @UniqueElementsBy(
            fieldNames = {"containerPath"},
            message = "duplicate host volume mounts")
    @Valid
    private List<HostVolumeMountConfigurationDto> hostVolumeMounts;

    private Map<String, String> annotations;

    /**
     * @param applyHostMounts when {@code false} the existing host volume mounts on the target are
     *     preserved untouched (non-admin update path); when {@code true} host mounts are fully
     *     CRUD-ed from the DTO (admin/owner path).
     */
    public void applyToEntity(
            GameServerEntity target,
            Function<Integer, GameEntity> gameProvider,
            boolean applyHostMounts) {
        target.setGame(gameProvider.apply(this.externalGameId));
        target.setServerName(this.getServerName());
        target.setDockerImageName(this.getDockerImageName());
        target.setDockerImageTag(this.getDockerImageTag());
        target.setDockerExecutionCommand(this.getExecutionCommand());
        target.setDockerHardwareLimits(this.getDockerHardwareLimits());
        target.setAnnotations(this.getAnnotations());

        target.setPortMappings(
                updateList(target.getPortMappings(), this.getPortMappings(), ArrayList::new));
        target.setEnvironmentVariables(
                updateList(
                        target.getEnvironmentVariables(),
                        this.getEnvironmentVariables(),
                        ArrayList::new));
        updateVolumeMounts(target);
        if (applyHostMounts) {
            updateHostVolumeMounts(target);
        }
    }

    private void updateVolumeMounts(GameServerEntity target) {
        List<VolumeMountConfiguration> existing = target.getVolumeMounts();
        if (existing == null) {
            existing = new ArrayList<>();
            target.setVolumeMounts(existing);
        }

        if (this.getVolumeMounts() == null) {
            existing.clear();
            return;
        }

        Map<String, VolumeMountConfiguration> existingByUuid =
                existing.stream()
                        .filter(vm -> vm.getUuid() != null)
                        .collect(Collectors.toMap(VolumeMountConfiguration::getUuid, vm -> vm));

        List<VolumeMountConfiguration> updated = new ArrayList<>();
        for (VolumeMountConfigurationDto dto : this.getVolumeMounts()) {
            if (dto.getUuid() != null && existingByUuid.containsKey(dto.getUuid())) {
                VolumeMountConfiguration reused = existingByUuid.get(dto.getUuid());
                reused.setContainerPath(dto.getContainerPath());
                updated.add(reused);
            } else {
                VolumeMountConfiguration fresh = VolumeMountConfiguration.fromDto(dto);
                fresh.setUuid(null);
                updated.add(fresh);
            }
        }

        existing.clear();
        existing.addAll(updated);
    }

    private void updateHostVolumeMounts(GameServerEntity target) {
        List<HostVolumeMountConfiguration> existing = target.getHostVolumeMounts();
        if (existing == null) {
            existing = new ArrayList<>();
            target.setHostVolumeMounts(existing);
        }

        if (this.getHostVolumeMounts() == null) {
            existing.clear();
            return;
        }

        Map<String, HostVolumeMountConfiguration> existingByUuid =
                existing.stream()
                        .filter(vm -> vm.getUuid() != null)
                        .collect(Collectors.toMap(HostVolumeMountConfiguration::getUuid, vm -> vm));

        List<HostVolumeMountConfiguration> updated = new ArrayList<>();
        for (HostVolumeMountConfigurationDto dto : this.getHostVolumeMounts()) {
            if (dto.getUuid() != null && existingByUuid.containsKey(dto.getUuid())) {
                HostVolumeMountConfiguration reused = existingByUuid.get(dto.getUuid());
                reused.setHostPath(dto.getHostPath());
                reused.setContainerPath(dto.getContainerPath());
                reused.setReadOnly(dto.getReadOnly() == null || dto.getReadOnly());
                updated.add(reused);
            } else {
                HostVolumeMountConfiguration fresh = HostVolumeMountConfiguration.fromDto(dto);
                fresh.setUuid(null);
                updated.add(fresh);
            }
        }

        existing.clear();
        existing.addAll(updated);
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
