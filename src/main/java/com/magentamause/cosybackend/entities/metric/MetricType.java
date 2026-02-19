package com.magentamause.cosybackend.entities.metric;

import lombok.Getter;

@Getter
public enum MetricType {
    CPU_PERCENT("CPU_PERCENT"),
    MEMORY_LIMIT("MEMORY_LIMIT"),
    MEMORY_PERCENT("MEMORY_PERCENT"),
    MEMORY_USAGE("MEMORY_USAGE"),
    NETWORK_INPUT("NETWORK_INPUT"),
    NETWORK_OUTPUT("NETWORK_OUTPUT"),
    BLOCK_READ("BLOCK_READ"),
    BLOCK_WRITE("BLOCK_WRITE");

    private final String value;

    MetricType(String value) {
        this.value = value;
    }
}
