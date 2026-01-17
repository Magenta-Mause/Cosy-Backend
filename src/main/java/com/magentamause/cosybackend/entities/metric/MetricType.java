package com.magentamause.cosybackend.entities.metric;

import lombok.Getter;

@Getter
public enum MetricType {
    CPU_PERCENT("cpu_percent"),
    MEMORY_LIMIT("memory_limit"),
    MEMORY_PERCENT("memory_percent"),
    MEMORY_USAGE("memory_usage"),
    NETWORK_INPUT("network_input"),
    NETWORK_OUTPUT("network_output"),
    BLOCK_READ("block_read"),
    BLOCK_WRITE("block_write");

    private final String value;

    MetricType(String value) {
        this.value = value;
    }
}
