package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.exceptions.ApiException;
import com.magentamause.cosybackend.exceptions.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HardwareLimitPresentValidator {

    public void validateHardwareLimitsPresent(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits == null) {
            // No present check needed because no limits set for user
            return;
        }
        if (serverLimits == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.HARDWARE_LIMIT_REQUIRED,
                    "Hardware limits are required for this user.");
        }

        validateCpuRequirements(userLimits, serverLimits);
        validateMemoryRequirements(userLimits, serverLimits);
    }

    private void validateCpuRequirements(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits.getDockerMaxCpuCores() == null) {
            // No present check needed because no CPU limits set for user
            return;
        }
        if (serverLimits.getDockerMaxCpuCores() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.HARDWARE_LIMIT_REQUIRED,
                    "CPU limit is required for this user.");
        }
    }

    private void validateMemoryRequirements(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits.getDockerMemoryLimit() == null) {
            // No present check needed because no memory limits set for user
            return;
        }
        if (serverLimits.getDockerMemoryLimit() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.HARDWARE_LIMIT_REQUIRED,
                    "Memory limit is required for this user.");
        }
    }
}
