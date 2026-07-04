package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.template.ExternalTemplateDto;
import com.magentamause.cosybackend.dtos.template.ResourceLimit;
import com.magentamause.cosybackend.dtos.template.TemplateHostMount;
import com.magentamause.cosybackend.dtos.template.TemplateVariable;
import jakarta.persistence.*;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String description;

    // v3: slug (e.g. "minecraft") or numeric-as-string (e.g. "38365"); stored RAW.
    @Column(nullable = false)
    private String gameId;

    @Column(nullable = false)
    private String dockerImageName;

    @Column(nullable = false)
    private String dockerImageTag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> environmentVariables;

    // v3: port values are string-or-number, stored as canonical strings (may hold {{var}}).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> portMappings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> dockerExecutionCommand;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> fileMounts;

    // v3: direct host bind mounts declared by the template ({host_path, container_path,
    // read_only}).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<TemplateHostMount> hostMounts;

    // v3: Docker container labels (may hold {{var}}); user-supplied values win at launch (P3).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> annotations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<TemplateVariable> variables;

    // v3: cpu/memory are string-or-number, stored as canonical strings (may hold {{var}}).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ResourceLimit resourceLimit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    public static TemplateEntity ofDto(ExternalTemplateDto externalTemplateDto) {
        return TemplateEntity.builder()
                .name(externalTemplateDto.name())
                .path(externalTemplateDto.path())
                .description(externalTemplateDto.description())
                .gameId(externalTemplateDto.gameId())
                .dockerImageName(externalTemplateDto.dockerImageName())
                .dockerImageTag(externalTemplateDto.dockerImageTag())
                .dockerExecutionCommand(externalTemplateDto.dockerExecutionCommand())
                .environmentVariables(externalTemplateDto.environmentVariables())
                .portMappings(externalTemplateDto.portMapping())
                .fileMounts(externalTemplateDto.fileMounts())
                .hostMounts(externalTemplateDto.hostMounts())
                .annotations(externalTemplateDto.annotations())
                .variables(externalTemplateDto.variables())
                .resourceLimit(externalTemplateDto.resourceLimit().orElse(null))
                .tags(externalTemplateDto.tags())
                .build();
    }
}
