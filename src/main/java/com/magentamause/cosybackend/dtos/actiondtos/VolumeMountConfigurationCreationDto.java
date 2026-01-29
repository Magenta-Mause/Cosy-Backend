package com.magentamause.cosybackend.dtos.actiondtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder
public class VolumeMountConfigurationCreationDto {
    // kept for legacy purposes.
    // TODO: remove when host path is fully removed from
    // frontend
    @NotBlank private String hostPath;

    @NotBlank private String containerPath;
}
