package com.magentamause.cosybackend.dtos.loki;

import java.util.List;
import java.util.Map;

public record LokiPushRequest(List<LokiStream> streams) {
    public record LokiStream(Map<String, String> stream, List<List<String>> values) {}
}
