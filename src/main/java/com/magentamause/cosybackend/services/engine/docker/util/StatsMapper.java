package com.magentamause.cosybackend.services.engine.docker.util;

import com.github.dockerjava.api.model.Statistics;
import com.magentamause.cosybackend.entities.metric.Metric;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class StatsMapper {

    private record ContainerState(
            long totalUsage,
            long systemUsage,
            long networkInput,
            long networkOutput,
            long blockRead,
            long blockWrite) {}

    private final Map<String, ContainerState> prevState = new ConcurrentHashMap<>();

    public Metric mapStats(String containerId, Statistics stats) {
        long usage =
                stats.getMemoryStats().getUsage() != null ? stats.getMemoryStats().getUsage() : 0L;
        long memoryLimit =
                stats.getMemoryStats().getLimit() != null ? stats.getMemoryStats().getLimit() : 1L;
        long inactiveFile =
                stats.getMemoryStats().getStats().getInactiveFile() != null
                        ? stats.getMemoryStats().getStats().getInactiveFile()
                        : 0L;

        long memoryUsage = usage - inactiveFile;
        double memoryPercent = memoryLimit > 0 ? (double) memoryUsage / memoryLimit * 100.0 : 0.0;

        long totalNetworkInput = 0;
        long totalNetworkOutput = 0;
        if (stats.getNetworks() != null) {
            for (var network : stats.getNetworks().values()) {
                totalNetworkInput += network.getRxBytes() != null ? network.getRxBytes() : 0L;
                totalNetworkOutput += network.getTxBytes() != null ? network.getTxBytes() : 0L;
            }
        }

        long totalBlockRead = 0;
        long totalBlockWrite = 0;
        if (stats.getBlkioStats() != null
                && stats.getBlkioStats().getIoServiceBytesRecursive() != null) {
            for (var stat : stats.getBlkioStats().getIoServiceBytesRecursive()) {
                if ("Read".equalsIgnoreCase(stat.getOp())) {
                    totalBlockRead += stat.getValue() != null ? stat.getValue() : 0L;
                } else if ("Write".equalsIgnoreCase(stat.getOp())) {
                    totalBlockWrite += stat.getValue() != null ? stat.getValue() : 0L;
                }
            }
        }

        long totalCpuUsage = safeLong(stats.getCpuStats().getCpuUsage().getTotalUsage());
        long systemCpuUsage = safeLong(stats.getCpuStats().getSystemCpuUsage());
        long cpuCount =
                stats.getCpuStats().getOnlineCpus() != null
                        ? stats.getCpuStats().getOnlineCpus()
                        : 1L;

        ContainerState prev = prevState.get(containerId);
        prevState.put(
                containerId,
                new ContainerState(
                        totalCpuUsage,
                        systemCpuUsage,
                        totalNetworkInput,
                        totalNetworkOutput,
                        totalBlockRead,
                        totalBlockWrite));

        if (prev == null) {
            return Metric.builder()
                    .cpuPercent(0.0)
                    .memoryUsage(memoryUsage)
                    .memoryLimit(memoryLimit)
                    .memoryPercent(memoryPercent)
                    .networkInput(0L)
                    .networkOutput(0L)
                    .blockRead(0L)
                    .blockWrite(0L)
                    .build();
        }

        double cpuDelta = (double) (totalCpuUsage - prev.totalUsage());
        double systemDelta = (double) (systemCpuUsage - prev.systemUsage());
        double cpuPercent =
                (systemDelta > 0 && cpuDelta >= 0)
                        ? cpuDelta / systemDelta * cpuCount * 100.0
                        : 0.0;

        long networkInputDelta = Math.max(0, totalNetworkInput - prev.networkInput());
        long networkOutputDelta = Math.max(0, totalNetworkOutput - prev.networkOutput());
        long blockReadDelta = Math.max(0, totalBlockRead - prev.blockRead());
        long blockWriteDelta = Math.max(0, totalBlockWrite - prev.blockWrite());

        return Metric.builder()
                .cpuPercent(cpuPercent)
                .memoryUsage(memoryUsage)
                .memoryLimit(memoryLimit)
                .memoryPercent(memoryPercent)
                .networkInput(networkInputDelta)
                .networkOutput(networkOutputDelta)
                .blockRead(blockReadDelta)
                .blockWrite(blockWriteDelta)
                .build();
    }

    public void clearState(String containerId) {
        prevState.remove(containerId);
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }
}
