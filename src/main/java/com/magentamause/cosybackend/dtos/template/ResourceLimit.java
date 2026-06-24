package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.magentamause.cosybackend.util.json.StringOrNumberDeserializer;

/**
 * Resource limits for a template. In v3 both {@code memory} and {@code cpu} are string-or-number
 * scalars so they can hold a concrete value (e.g. {@code "2GiB"} / {@code "2"}) or an unresolved
 * {@code {{var}}} placeholder. They are stored RAW as canonical strings.
 */
public record ResourceLimit(
        @JsonDeserialize(using = StringOrNumberDeserializer.class) String memory,
        @JsonDeserialize(using = StringOrNumberDeserializer.class) String cpu) {}
