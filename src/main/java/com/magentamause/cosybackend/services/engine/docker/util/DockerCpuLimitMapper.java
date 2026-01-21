package com.magentamause.cosybackend.services.engine.docker.util;

public class DockerCpuLimitMapper {
    private static final Long MULTIPLIER = 1_000_000L;

    public static Long toNanoCpu(float cpuLimit) {
        return (long) (cpuLimit * MULTIPLIER);
    }
}
