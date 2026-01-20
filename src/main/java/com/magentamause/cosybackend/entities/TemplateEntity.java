package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.template.ResourceLimit;
import com.magentamause.cosybackend.dtos.template.ExternalTemplateDto;
import com.magentamause.cosybackend.dtos.template.Variable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

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
    @Column(nullable = false)
    private int gameId;

    @Column(nullable = false)
    private String dockerImageName;
    @Column(nullable = false)
    private String dockerImageTag;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> environmentVariables;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Number> portMappings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> dockerExecutionCommand;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> fileMounts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Variable> variables;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ResourceLimit resourceLimit;

    public static TemplateEntity ofDto(ExternalTemplateDto externalTemplateDto) {
        return TemplateEntity.builder()
                .name(externalTemplateDto.name())
                .path(externalTemplateDto.path())
                .description(externalTemplateDto.description())
                .gameId(externalTemplateDto.gameId())
                .dockerImageName(externalTemplateDto.dockerImageName())
                .dockerImageTag(externalTemplateDto.dockerImageTag())
                .environmentVariables(externalTemplateDto.environmentVariables())
                .portMappings(externalTemplateDto.portMapping())
                .fileMounts(externalTemplateDto.fileMounts())
                .variables(externalTemplateDto.variables())
                .resourceLimit(externalTemplateDto.resourceLimit().orElse(null))
                .build();
    }
}
