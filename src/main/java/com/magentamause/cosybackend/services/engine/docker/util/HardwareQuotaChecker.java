package com.magentamause.cosybackend.services.engine.docker.util;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HardwareQuotaChecker {

    public record ResourceUsage(double cpu, long memoryBytes) {
        public ResourceUsage add(ResourceUsage other) {
            return new ResourceUsage(this.cpu + other.cpu, this.memoryBytes + other.memoryBytes);
        }
    }

    public void assertSufficientQuota(
            UserEntity user, GameServerEntity serverToStart, List<GameServerEntity> servers) {
        if (user.getDockerHardwareLimits() == null) {
            // No quota check needed because no limits set for user
            return;
        }

        ResourceUsage currentUsage = calculateRunningServersUsage(servers);
        ResourceUsage requiredUsage = calculateServerUsage(serverToStart);

        checkUsageAgainstLimits(currentUsage.add(requiredUsage), getUsageLimits(user));
    }

    private void checkUsageAgainstLimits(ResourceUsage totalUsage, ResourceUsage userLimits) {
        if (totalUsage.cpu > userLimits.cpu || totalUsage.memoryBytes > userLimits.memoryBytes) {
            throw new HardwareLimitException(totalUsage, userLimits);
        }
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

    private ResourceUsage calculateRunningServersUsage(List<GameServerEntity> servers) {
        double totalCpu = 0.0;
        long totalMem = 0L;

        if (servers == null) {
            return new ResourceUsage(0, 0);
        }

        for (GameServerEntity server : servers) {
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
