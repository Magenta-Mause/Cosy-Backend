package com.magentamause.cosybackend.entities.utility;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.actiondtos.VolumeMountConfigurationCreationDto;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VolumeMountConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false)
    private String containerPath;

    public static VolumeMountConfiguration fromDto(VolumeMountConfigurationCreationDto dto) {
        return VolumeMountConfiguration.builder().containerPath(dto.getContainerPath()).build();
    }
}
