package com.magentamause.cosybackend.dtos.loki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiData(String resultType, List<LokiStreamResult> result, LokiStats stats) {}
