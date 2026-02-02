package com.magentamause.cosybackend.exceptions;

import com.magentamause.cosybackend.services.engine.docker.util.HardwareQuotaChecker;
import com.magentamause.cosybackend.services.engine.docker.util.MemoryUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class HardwareLimitException extends RuntimeException {
    public HardwareLimitException(
            HardwareQuotaChecker.ResourceUsage totalUsage,
            HardwareQuotaChecker.ResourceUsage userLimit) {
        super(buildString(totalUsage, userLimit));
    }

    private static String buildString(
            HardwareQuotaChecker.ResourceUsage totalUsage,
            HardwareQuotaChecker.ResourceUsage userLimits) {
        StringBuilder sb =
                new StringBuilder("Could not start Server - user Hardware limit was reached:\n");
        if (userLimits.cpu() < Double.MAX_VALUE) {
            sb.append(String.format("cpu-cores: %.2f/%.2f\n", totalUsage.cpu(), userLimits.cpu()));
        }
        if (userLimits.memoryBytes() < Long.MAX_VALUE) {
            sb.append(
                    String.format(
                            "memory: %s/%s",
                            MemoryUtils.formatBytesToReadableString(totalUsage.memoryBytes()),
                            MemoryUtils.formatBytesToReadableString(userLimits.memoryBytes())));
        }
        return sb.toString().trim();
    }
}
