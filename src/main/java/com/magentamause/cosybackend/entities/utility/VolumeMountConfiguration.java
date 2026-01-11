package com.magentamause.cosybackend.entities.utility;

import com.magentamause.cosybackend.dtos.actiondtos.VolumeMountConfigurationCreationDto;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

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
    private String hostPath;

    @Column(nullable = false)
    private String containerPath;

    public static VolumeMountConfiguration fromDto(VolumeMountConfigurationCreationDto dto) {
        return VolumeMountConfiguration.builder()
                .hostPath(dto.getHostPath())
                .containerPath(dto.getContainerPath())
                .build();
    }
}
