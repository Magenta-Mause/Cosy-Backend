package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HardwareQuotaService {

    private record ResourceUsage(double cpu, long memoryBytes) {
        public ResourceUsage add(ResourceUsage other) {
            return new ResourceUsage(this.cpu + other.cpu, this.memoryBytes + other.memoryBytes);
        }
    }

    public void assertHardwareLimitsPresent(DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits == null) {
            // No need for further validation
            return;
        }

        if (serverLimits == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Hardware limits are required for this user.");
        }

        assertCpuPresent(userLimits, serverLimits);
        assertMemoryPresent(userLimits, serverLimits);
    }

    public void assertSufficientQuota(GameServerEntity serverToStart) {
        UserEntity startedBy = serverToStart.getLastStartedBy();
        if (startedBy.getDockerHardwareLimits() == null) {
            // No need for further validation
            return;
        }

        ResourceUsage currentUsage =
                calculateRunningServersUsage(startedBy, serverToStart.getUuid());
        ResourceUsage requiredUsage = calculateServerUsage(serverToStart);

        checkUsageAgainstLimits(currentUsage.add(requiredUsage), getUsageLimits(startedBy));
    }

    private void assertCpuPresent(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits.getDockerMaxCpuCores() == null) {
            // No need for further validation
            return;
        }
        if (serverLimits.getDockerMaxCpuCores() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "CPU limit is required for this user.");
        }
    }

    private void assertMemoryPresent(
            DockerHardwareLimits userLimits, DockerHardwareLimits serverLimits) {
        if (userLimits.getDockerMemoryLimit() == null) {
            // No need for further validation
            return;
        }
        if (serverLimits.getDockerMemoryLimit() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Memory limit is required for this user.");
        }
    }

    private void checkUsageAgainstLimits(ResourceUsage totalUsage, ResourceUsage userLimits) {
        if (totalUsage.cpu > userLimits.cpu || totalUsage.memoryBytes > userLimits.memoryBytes) {
            throw new HardwareLimitException(createLimitExceededMessage(totalUsage, userLimits));
        }
    }

    private String createLimitExceededMessage(ResourceUsage totalUsage, ResourceUsage userLimits) {
        StringBuilder sb =
                new StringBuilder("Could not start Server - user Hardware limit was reached:\n");
        if (userLimits.cpu < Double.MAX_VALUE) {
            sb.append(String.format("cpu-cores: %.2f/%.2f\n", totalUsage.cpu, userLimits.cpu));
        }
        if (userLimits.memoryBytes < Long.MAX_VALUE) {
            sb.append(
                    String.format(
                            "memory: %s/%s",
                            MemoryUtils.formatBytesToReadableString(totalUsage.memoryBytes),
                            MemoryUtils.formatBytesToReadableString(userLimits.memoryBytes)));
        }
        return sb.toString().trim();

    }

    private ResourceUsage getUsageLimits(UserEntity user) {
        DockerHardwareLimits limits = user.getDockerHardwareLimits();
        double cpu =
                limits.getDockerMaxCpuCores() != null
                        ? limits.getDockerMaxCpuCores()
                        : Double.MAX_VALUE;
        long mem =
                limits.getDockerMemoryLimit() != null
                        ? MemoryUtils.parseMemoryStringToBytes(limits.getDockerMemoryLimit())
                        : Long.MAX_VALUE;
        return new ResourceUsage(cpu, mem);
    }

    private ResourceUsage calculateRunningServersUsage(UserEntity user, String excludeUuid) {
        double totalCpu = 0.0;
        long totalMem = 0L;

        List<GameServerEntity> servers = user.getStartedServers();
        if (servers == null) {
            return new ResourceUsage(0, 0);
        }

        for (GameServerEntity server : servers) {
            if (Objects.equals(server.getUuid(), excludeUuid)) {
                continue;
            }
            if (isRunning(server)) {
                ResourceUsage usage = calculateServerUsage(server);
                totalCpu += usage.cpu;
                totalMem += usage.memoryBytes;
            }
        }
        return new ResourceUsage(totalCpu, totalMem);
    }

    private boolean isRunning(GameServerEntity server) {
        return server.getStatus() != GameServerDto.GameServerStatus.STOPPED
                && server.getStatus() != GameServerDto.GameServerStatus.FAILED;
    }

    private ResourceUsage calculateServerUsage(GameServerEntity server) {
        DockerHardwareLimits limits = server.getDockerHardwareLimits();
        if (limits == null) {
            return new ResourceUsage(0, 0);
        }

        double cpu = limits.getDockerMaxCpuCores() != null ? limits.getDockerMaxCpuCores() : 0.0;
        long mem =
                limits.getDockerMemoryLimit() != null
                        ? MemoryUtils.parseMemoryStringToBytes(limits.getDockerMemoryLimit())
                        : 0L;

        return new ResourceUsage(cpu, mem);
    }
}
