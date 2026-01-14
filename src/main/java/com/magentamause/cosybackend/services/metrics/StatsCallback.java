package com.magentamause.cosybackend.services.metrics;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import com.magentamause.cosybackend.entities.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@Slf4j
@RequiredArgsConstructor
public class StatsCallback extends ResultCallback.Adapter<Statistics> {
    private final Metrics.MetricsBuilder builder;
    private final CountDownLatch latch;

    @Override
    public void onNext(Statistics stats) {
        if (stats.getCpuStats() == null ||
                stats.getCpuStats().getCpuUsage() == null ||
                stats.getCpuStats().getSystemCpuUsage() == null ||
                stats.getPreCpuStats() == null ||
                stats.getPreCpuStats().getCpuUsage() == null ||
                stats.getPreCpuStats().getSystemCpuUsage() == null) {
            return; // Wait for next stats event
        }

        double cpuPercent = getCpuPercent(stats);

        long memoryUsage = stats.getMemoryStats().getUsage() != null ?
                stats.getMemoryStats().getUsage() : 0L;
        long memoryLimit = stats.getMemoryStats().getLimit() != null ?
                stats.getMemoryStats().getLimit() : 1L;
        double memoryPercent = memoryLimit > 0 ?
                (double) memoryUsage / memoryLimit * 100.0 : 0.0;

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
        if (stats.getBlkioStats() != null && stats.getBlkioStats().getIoServiceBytesRecursive() != null) {
            for (var stat : stats.getBlkioStats().getIoServiceBytesRecursive()) {
                if ("Read".equals(stat.getOp())) {
                    blockRead += stat.getValue() != null ? stat.getValue() : 0L;
                } else if ("Write".equals(stat.getOp())) {
                    blockWrite += stat.getValue() != null ? stat.getValue() : 0L;
                }
            }
        }

        builder
                .cpuPercent(cpuPercent)
                .memoryUsage(memoryUsage)
                .memoryLimit(memoryLimit)
                .memoryPercent(memoryPercent)
                .networkInput(networkInput)
                .networkOutput(networkOutput)
                .blockRead(blockRead)
                .blockWrite(blockWrite);

        latch.countDown();

        try {
            close();
        } catch (IOException e) {
            // Ignore - we already got the stats
        }
    }

    private static double getCpuPercent(Statistics stats) {
        double cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage() -
                stats.getPreCpuStats().getCpuUsage().getTotalUsage();
        double systemDelta = stats.getCpuStats().getSystemCpuUsage() -
                stats.getPreCpuStats().getSystemCpuUsage();
        Long cpuCountLong = stats.getCpuStats().getOnlineCpus();
        long cpuCount = cpuCountLong != null ? cpuCountLong : 1L;

        double cpuPercent = 0.0;
        if (systemDelta > 0.0 && cpuDelta > 0.0) {
            cpuPercent = (cpuDelta / systemDelta) * cpuCount * 100.0;
        }
        return cpuPercent;
    }
}
