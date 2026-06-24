package com.magentamause.cosybackend.entities.gameserver.utility;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.actiondtos.gameserver.HostVolumeMountConfigurationDto;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class HostVolumeMountConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false)
    private String hostPath;

    @Column(nullable = false)
    private String containerPath;

    private boolean readOnly;

    public static HostVolumeMountConfiguration fromDto(HostVolumeMountConfigurationDto dto) {
        return HostVolumeMountConfiguration.builder()
                .uuid(dto.getUuid())
                .hostPath(dto.getHostPath())
                .containerPath(dto.getContainerPath())
                .readOnly(dto.getReadOnly() == null || dto.getReadOnly())
                .build();
    }
}
