package com.magentamause.cosybackend.services.engine.docker.util;

import com.github.dockerjava.api.model.Statistics;
import com.magentamause.cosybackend.entities.metric.Metric;
import org.springframework.stereotype.Component;

@Component
public class StatsMapper {
    public Metric mapStats(Statistics stats) {
        double cpuPercent = getCpuPercent(stats);
        long memoryUsage =
                stats.getMemoryStats().getUsage() != null ? stats.getMemoryStats().getUsage() : 0L;
        long memoryLimit =
                stats.getMemoryStats().getLimit() != null ? stats.getMemoryStats().getLimit() : 1L;
        long inactiveFile =
                stats.getMemoryStats().getStats().getInactiveFile() != null
                        ? stats.getMemoryStats().getStats().getInactiveFile()
                        : 0L;

        long usedMemory = memoryUsage - inactiveFile;

        double memoryPercent = round(memoryLimit > 0 ? (double) usedMemory / memoryLimit * 100.0 : 0.0);

        long networkInput = 0;
        long networkOutput = 0;
        if (stats.getNetworks() != null) {
            for (var network : stats.getNetworks().values()) {
                networkInput += network.getRxBytes() != null ? network.getRxBytes() : 0L;
                networkOutput += network.getTxBytes() != null ? network.getTxBytes() : 0L;
            }
        }

        long blockRead = 0;
        long blockWrite = 0;
        if (stats.getBlkioStats() != null
                && stats.getBlkioStats().getIoServiceBytesRecursive() != null) {
            for (var stat : stats.getBlkioStats().getIoServiceBytesRecursive()) {
                if ("Read".equalsIgnoreCase(stat.getOp())) {
                    blockRead += stat.getValue() != null ? stat.getValue() : 0L;
                } else if ("Write".equalsIgnoreCase(stat.getOp())) {
                    blockWrite += stat.getValue() != null ? stat.getValue() : 0L;
                }
            }
        }

        return Metric.builder()
                .cpuPercent(cpuPercent)
                .memoryUsage(memoryUsage)
                .memoryLimit(memoryLimit)
                .memoryPercent(memoryPercent)
                .networkInput(networkInput)
                .networkOutput(networkOutput)
                .blockRead(blockRead)
                .blockWrite(blockWrite)
                .build();
    }

    private Long prevTotalUsage = null;
    private Long prevSystemUsage = null;

    private double getCpuPercent(Statistics stats) {
        Long totalUsage = safeLong(stats.getCpuStats().getCpuUsage().getTotalUsage());
        Long systemUsage = safeLong(stats.getCpuStats().getSystemCpuUsage());

        long cpuCount =
                stats.getCpuStats().getOnlineCpus() != null
                        ? stats.getCpuStats().getOnlineCpus()
                        : 1L;

        if (prevTotalUsage == null || prevSystemUsage == null) {
            prevTotalUsage = totalUsage;
            prevSystemUsage = systemUsage;
            return 0.0;
        }

        double cpuDelta = (double) (totalUsage - prevTotalUsage);
        double systemDelta = (double) (systemUsage - prevSystemUsage);

        prevTotalUsage = totalUsage;
        prevSystemUsage = systemUsage;

        if (systemDelta <= 0 || cpuDelta < 0) {
            return 0.0;
        }

        return round(cpuDelta / systemDelta * cpuCount * 100.0);
    }

    private Long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
