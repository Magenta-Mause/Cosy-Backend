package com.magentamause.cosybackend.dtos.loki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiStats(LokiSummary summary) {}
