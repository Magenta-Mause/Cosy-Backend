package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A direct host volume bind mount declared by a template ({@code host_mounts[]} in the v3 wire
 * contract).
 *
 * <p>{@code read_only} is ALWAYS sent as an explicit boolean by the template-service (a
 * YAML-omitted value is emitted as {@code true}). The backend must NOT reapply a default — trust
 * the sent value.
 */
public record TemplateHostMount(
        @JsonProperty("host_path") String hostPath,
        @JsonProperty("container_path") String containerPath,
        @JsonProperty("read_only") boolean readOnly) {}
