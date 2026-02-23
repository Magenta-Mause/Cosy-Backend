package com.magentamause.cosybackend.entities.gameserver.utility;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.VolumeMountConfigurationDto;
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

    public static VolumeMountConfiguration fromDto(VolumeMountConfigurationDto dto) {
        return VolumeMountConfiguration.builder()
                .uuid(dto.getUuid())
                .containerPath(dto.getContainerPath())
                .build();
    }
}
