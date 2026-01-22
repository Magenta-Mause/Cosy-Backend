package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VolumeMountConfigurationCreationDto {
    // kept for legacy purposes, remove when host path is fully removed from
    // frontend
    @NotBlank private String hostPath;

    @NotBlank private String containerPath;
}
