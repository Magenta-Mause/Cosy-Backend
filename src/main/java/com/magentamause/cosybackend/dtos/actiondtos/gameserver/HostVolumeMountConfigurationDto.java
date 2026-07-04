package com.magentamause.cosybackend.dtos.actiondtos.gameserver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HostVolumeMountConfigurationDto {
    private String uuid;

    @NotBlank private String hostPath;

    @NotBlank
    @Pattern(regexp = "^/.+", message = "Container path must be an absolute path starting with '/'")
    private String containerPath;

    @Builder.Default private Boolean readOnly = true;
}
