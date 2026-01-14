package com.magentamause.cosybackend.dtos.loki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiQueryResponse(String status, LokiData data) {}
