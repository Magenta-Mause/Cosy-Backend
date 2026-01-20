package com.magentamause.cosybackend.dtos.template;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Variable(
        String name,
        String type,
        String placeholder,
        String regex,
        @JsonProperty("default_value") Object defaultValue,
        List<String> options
) {
}
