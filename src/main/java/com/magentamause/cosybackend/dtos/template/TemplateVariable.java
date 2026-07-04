package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A template variable definition. The v3 wire contract emits {@code default} (not the v2 {@code
 * default_value}) plus {@code required}; {@code default} and {@code example} are free-form scalars
 * (string or number) and are kept as {@link Object}.
 */
public record TemplateVariable(
        String name,
        String type,
        String placeholder,
        String regex,
        @JsonProperty("default") @JsonAlias("default_value") Object defaultValue,
        List<String> options,
        Boolean required,
        Object example,
        String description) {}
