package com.magentamause.cosybackend.services.metrics;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import com.magentamause.cosybackend.entities.Metrics;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CountDownLatch;

@RequiredArgsConstructor
public class StatsCallback extends ResultCallback.Adapter<Statistics> {
    private final Metrics.MetricsBuilder builder;
    private final CountDownLatch latch;


    @Override
    public void onNext(Statistics stats) {
        double cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage() -
                stats.getPreCpuStats().getCpuUsage().getTotalUsage();
        double systemDelta = stats.getCpuStats().getSystemCpuUsage() -
                stats.getPreCpuStats().getSystemCpuUsage();
        long cpuCount = stats.getCpuStats().getOnlineCpus();

        double cpuPercent = 0.0;
        if (systemDelta > 0.0 && cpuDelta > 0.0) {
            cpuPercent = (cpuDelta / systemDelta) * cpuCount * 100.0;
        }

        long memoryUsage = stats.getMemoryStats().getUsage();
        long memoryLimit = stats.getMemoryStats().getLimit();
        double memoryPercent = (double) memoryUsage / memoryLimit * 100.0;

        long networkInput = 0;
        long networkOutput = 0;
        if (stats.getNetworks() != null) {
            for (var network : stats.getNetworks().values()) {
                networkInput += network.getRxBytes();
                networkOutput += network.getTxBytes();
            }
        }

        long blockRead = 0;
        long blockWrite = 0;
        if (stats.getBlkioStats() != null && stats.getBlkioStats().getIoServiceBytesRecursive() != null) {
            for (var stat : stats.getBlkioStats().getIoServiceBytesRecursive()) {
                if ("Read".equals(stat.getOp())) {
                    blockRead += stat.getValue();
                } else if ("Write".equals(stat.getOp())) {
                    blockWrite += stat.getValue();
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
        onComplete();
    }
}
