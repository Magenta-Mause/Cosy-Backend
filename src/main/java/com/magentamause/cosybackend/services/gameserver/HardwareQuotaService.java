package com.magentamause.cosybackend.services.gameserver;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import com.magentamause.cosybackend.services.engine.docker.util.MemoryUtils;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class HardwareQuotaService {

    private record ResourceUsage(double cpu, long memoryBytes) {
        public ResourceUsage add(ResourceUsage other) {
            return new ResourceUsage(this.cpu + other.cpu, this.memoryBytes + other.memoryBytes);
        }
    }

    public void validateHardwareLimits(GameServerEntity serverToStart) {
        UserEntity owner = serverToStart.getOwner();
        if (!hasHardwareLimits(owner)) {
            return;
        }

        ResourceUsage currentUsage = calculateRunningServersUsage(owner, serverToStart.getUuid());
        ResourceUsage requiredUsage = calculateServerUsage(serverToStart);
        ResourceUsage totalUsage = currentUsage.add(requiredUsage);

        ResourceUsage userLimits = getUsageLimits(owner);

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

    private boolean hasHardwareLimits(UserEntity user) {
        if (user == null || user.getDockerHardwareLimits() == null) {
            return false;
        }
        DockerHardwareLimits limits = user.getDockerHardwareLimits();
        return limits.getDockerMaxCpuCores() != null || limits.getDockerMemoryLimit() != null;
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

    private ResourceUsage calculateRunningServersUsage(UserEntity owner, String excludeUuid) {
        double totalCpu = 0.0;
        long totalMem = 0L;

        List<GameServerEntity> servers = owner.getGameServerConfigurationEntities();
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
