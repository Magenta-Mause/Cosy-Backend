package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.entities.utility.DockerHardwareLimits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HardwareLimitPresentValidator {

    public void validateHardwareLimitsPresent(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits == null) {
            // No present check needed because no limits set for user
            return;
        }
        if (serverLimits == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Hardware limits are required for this user.");
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "CPU limit is required for this user.");
        }
    }

    private void validateMemoryRequirements(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits.getDockerMemoryLimit() == null) {
            // No present check needed because no memory limits set for user
            return;
        }
        if (serverLimits.getDockerMemoryLimit() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Memory limit is required for this user.");
        }
    }
}
