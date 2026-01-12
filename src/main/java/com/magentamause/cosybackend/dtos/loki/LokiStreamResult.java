package com.magentamause.cosybackend.dtos.loki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiStreamResult(Map<String, String> stream, List<List<String>> values) {}
