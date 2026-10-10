package com.magentamause.cosybackend.exceptions;

import com.magentamause.cosybackend.services.engine.docker.util.HardwareQuotaChecker;
import com.magentamause.cosybackend.services.engine.docker.util.MemoryUtils;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.FORBIDDEN)
public class HardwareLimitException extends RuntimeException {
    private final ErrorCode errorCode;

    public HardwareLimitException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public HardwareLimitException(
            HardwareQuotaChecker.ResourceUsage totalUsage,
            HardwareQuotaChecker.ResourceUsage userLimit) {
        super(buildString(totalUsage, userLimit));
        this.errorCode = ErrorCode.HARDWARE_QUOTA_EXCEEDED;
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
