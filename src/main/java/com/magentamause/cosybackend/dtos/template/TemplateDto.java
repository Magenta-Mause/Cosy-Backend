package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Game Server Template - corresponds to schema/template.schema.json
 */
// @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class) - Removed as WebClient requires @JsonProperty definition anyways
public record TemplateDto(
        String name,
        String path,
        String description,
        @JsonProperty("game_id") int gameId,

        @JsonProperty("docker_image_name") String dockerImageName,
        @JsonProperty("docker_image_tag") String dockerImageTag,
        @JsonProperty("docker_execution_command") List<String> dockerExecutionCommand,
        @JsonProperty("environment_variables") Map<String, String> environmentVariables,
        @JsonProperty("port_mapping") Map<String, Number> portMapping,
        @JsonProperty("file_mounts") List<String> fileMounts,
        @JsonProperty("resource_limit") Optional<ResourceLimit> resourceLimit,
        List<Variable> variables
) {
}
