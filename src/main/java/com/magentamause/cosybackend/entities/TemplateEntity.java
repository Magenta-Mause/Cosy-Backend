package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.template.ResourceLimit;
import com.magentamause.cosybackend.dtos.template.TemplateDto;
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

    public static TemplateEntity ofDto(TemplateDto templateDto) {
        return TemplateEntity.builder()
                .name(templateDto.name())
                .path(templateDto.path())
                .description(templateDto.description())
                .gameId(templateDto.gameId())
                .dockerImageName(templateDto.dockerImageName())
                .dockerImageTag(templateDto.dockerImageTag())
                .environmentVariables(templateDto.environmentVariables())
                .portMappings(templateDto.portMapping())
                .fileMounts(templateDto.fileMounts())
                .variables(templateDto.variables())
                .resourceLimit(templateDto.resourceLimit().orElse(null))
                .build();
    }
}
