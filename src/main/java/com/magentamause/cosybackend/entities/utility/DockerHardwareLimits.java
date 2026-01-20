package com.magentamause.cosybackend.entities.utility;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.annotations.docker.ValidMemoryLimit;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DockerHardwareLimits {
    @Min(value = 1, message = "CPU cores must be at least 1")
    private Long dockerMaxCpuCores;

    @ValidMemoryLimit private String dockerMemoryLimit;
}
