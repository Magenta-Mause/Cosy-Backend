package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.magentamause.cosybackend.util.json.StringOrNumberMapDeserializer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// WebClient parsing requires @JsonProperty definition - @JsonNaming not sufficient here.
// Matches the LOCKED v3 wire contract emitted by cosy-template-service (GET /v3/templates).
// Lenient string-or-number fields (resource_limit.cpu/memory, port_mapping values) are
// stored RAW as their canonical String form; {{var}} placeholders are preserved untouched.
public record ExternalTemplateDto(
        String name,
        String path,
        String description,
        @JsonProperty("game_id") String gameId,
        @JsonProperty("docker_image_name") String dockerImageName,
        @JsonProperty("docker_image_tag") String dockerImageTag,
        @JsonProperty("docker_execution_command") List<String> dockerExecutionCommand,
        @JsonProperty("environment_variables") Map<String, String> environmentVariables,
        @JsonProperty("port_mapping") @JsonDeserialize(using = StringOrNumberMapDeserializer.class)
                Map<String, String> portMapping,
        @JsonProperty("file_mounts") List<String> fileMounts,
        @JsonProperty("host_mounts") List<TemplateHostMount> hostMounts,
        @JsonProperty("annotations") Map<String, String> annotations,
        @JsonProperty("resource_limit") Optional<ResourceLimit> resourceLimit,
        List<TemplateVariable> variables,
        @JsonProperty("tags") List<String> tags) {}
