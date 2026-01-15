package com.magentamause.cosybackend.services.engine.docker.util;

import com.github.dockerjava.api.model.Statistics;
import com.magentamause.cosybackend.entities.metric.Metric;
import org.springframework.stereotype.Component;

@Component
public class StatsMapper {
    public void mapStats(Statistics stats, Metric.MetricBuilder builder) {
        double cpuPercent = getCpuPercent(stats);

        long memoryUsage =
                stats.getMemoryStats().getUsage() != null ? stats.getMemoryStats().getUsage() : 0L;
        long memoryLimit =
                stats.getMemoryStats().getLimit() != null ? stats.getMemoryStats().getLimit() : 1L;
        double memoryPercent = memoryLimit > 0 ? (double) memoryUsage / memoryLimit * 100.0 : 0.0;

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
                if ("Read".equals(stat.getOp())) {
                    blockRead += stat.getValue() != null ? stat.getValue() : 0L;
                } else if ("Write".equals(stat.getOp())) {
                    blockWrite += stat.getValue() != null ? stat.getValue() : 0L;
                }
            }
        }

        builder.cpuPercent(cpuPercent)
                .memoryUsage(memoryUsage)
                .memoryLimit(memoryLimit)
                .memoryPercent(memoryPercent)
                .networkInput(networkInput)
                .networkOutput(networkOutput)
                .blockRead(blockRead)
                .blockWrite(blockWrite);
    }

    private double getCpuPercent(Statistics stats) {
        Long totalUsage = safeLong(stats.getCpuStats().getCpuUsage().getTotalUsage());
        Long preTotalUsage = safeLong(stats.getPreCpuStats().getCpuUsage().getTotalUsage());

        Long systemUsage = safeLong(stats.getCpuStats().getSystemCpuUsage());
        Long preSystemUsage = safeLong(stats.getPreCpuStats().getSystemCpuUsage());

        long cpuCount =
                stats.getCpuStats().getOnlineCpus() != null
                        ? stats.getCpuStats().getOnlineCpus()
                        : 1L;

        double cpuDelta = totalUsage - preTotalUsage;
        double systemDelta = systemUsage - preSystemUsage;

        if (systemDelta > 0 && cpuDelta > 0) {
            return (cpuDelta / systemDelta) * cpuCount * 100.0;
        }
        return 0.0;
    }

    private Long safeLong(Long value) {
        return value != null ? value : 0L;
    }
}
