package com.magentamause.cosybackend.dtos.loki;

import java.time.Instant;
import java.util.Map;

public record LokiLogEntry(Instant timestamp, String message, Map<String, String> labels) {}
