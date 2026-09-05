package com.example.xingmang.benchmark.upload;

import com.example.xingmang.exception.ConditionException;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UploadBenchmarkMetrics {

    private static final long HEAP_SAMPLE_INTERVAL_MS = 50L;

    private final Object lock = new Object();
    private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();

    private Session activeSession;
    private long appFileIngressBytes;
    private long appFileEgressBytes;

    public Map<String, Object> start() {
        synchronized (lock) {
            if (activeSession != null) {
                throw new ConditionException(400, "已有 Benchmark session 尚未 stop");
            }

            long heapUsedStartBytes = getHeapUsedBytes();
            AtomicLong maxHeapUsedBytes = new AtomicLong(heapUsedStartBytes);
            ScheduledExecutorService heapSampler = newHeapSampler();

            Session session = new Session(
                    UUID.randomUUID().toString(),
                    System.nanoTime(),
                    heapUsedStartBytes,
                    maxHeapUsedBytes,
                    heapSampler,
                    getProcessCpuTimeNs(),
                    getGcCount(),
                    getGcTimeMs()
            );
            heapSampler.scheduleAtFixedRate(
                    () -> updateMaxHeapUsed(maxHeapUsedBytes),
                    0L,
                    HEAP_SAMPLE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
            activeSession = session;
            appFileIngressBytes = 0L;
            appFileEgressBytes = 0L;

            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", activeSession.sessionId);
            return result;
        }
    }

    public void addAppFileTraffic(long ingressBytes, long egressBytes) {
        synchronized (lock) {
            appFileIngressBytes += Math.max(0L, ingressBytes);
            appFileEgressBytes += Math.max(0L, egressBytes);
        }
    }

    public Map<String, Object> stop(String sessionId) {
        synchronized (lock) {
            if (activeSession == null) {
                throw new ConditionException(400, "当前没有 active Benchmark session");
            }
            if (!activeSession.sessionId.equals(sessionId)) {
                throw new ConditionException(400, "sessionId 不匹配");
            }

            long endNs = System.nanoTime();
            long elapsedNs = endNs - activeSession.startNs;
            long heapUsedEndBytes = getHeapUsedBytes();
            updateMaxHeapUsed(activeSession.maxHeapUsedBytes);
            long heapPeakBytes = activeSession.maxHeapUsedBytes.get();
            Long processCpuTimeEndNs = getProcessCpuTimeNs();
            Long processCpuTimeMs = null;
            Double processCpuUtilizationPct = null;

            if (activeSession.processCpuTimeStartNs != null && processCpuTimeEndNs != null && elapsedNs > 0L) {
                long cpuDeltaNs = processCpuTimeEndNs - activeSession.processCpuTimeStartNs;
                processCpuTimeMs = Math.max(0L, cpuDeltaNs) / 1_000_000L;
                int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
                processCpuUtilizationPct = Math.max(0.0, cpuDeltaNs * 100.0 / elapsedNs / processors);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", activeSession.sessionId);
            result.put("elapsedMs", nsToMs(elapsedNs));
            result.put("heapUsedStartMb", bytesToMb(activeSession.heapUsedStartBytes));
            result.put("heapUsedEndMb", bytesToMb(heapUsedEndBytes));
            result.put("heapPeakMb", bytesToMb(heapPeakBytes));
            result.put("heapPeakDeltaMb", bytesToMb(heapPeakBytes - activeSession.heapUsedStartBytes));
            result.put("processCpuTimeMs", processCpuTimeMs);
            result.put("processCpuUtilizationPct", processCpuUtilizationPct);
            result.put("gcCountDelta", getGcCount() - activeSession.gcCountStart);
            result.put("gcTimeDeltaMs", getGcTimeMs() - activeSession.gcTimeStartMs);
            result.put("appFileIngressBytes", appFileIngressBytes);
            result.put("appFileEgressBytes", appFileEgressBytes);

            activeSession.heapSampler.shutdownNow();
            activeSession = null;
            appFileIngressBytes = 0L;
            appFileEgressBytes = 0L;
            return result;
        }
    }

    private ScheduledExecutorService newHeapSampler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "upload-benchmark-heap-sampler");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void updateMaxHeapUsed(AtomicLong maxHeapUsedBytes) {
        long current = getHeapUsedBytes();
        maxHeapUsedBytes.accumulateAndGet(current, Math::max);
    }

    private long getHeapUsedBytes() {
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        return heap.getUsed();
    }

    private Long getProcessCpuTimeNs() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            long value = osBean.getProcessCpuTime();
            return value >= 0L ? value : null;
        }
        return null;
    }

    private long getGcCount() {
        long total = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0L) {
                total += count;
            }
        }
        return total;
    }

    private long getGcTimeMs() {
        long total = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = gc.getCollectionTime();
            if (time >= 0L) {
                total += time;
            }
        }
        return total;
    }

    private double nsToMs(long ns) {
        return ns / 1_000_000.0;
    }

    private double bytesToMb(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    private record Session(
            String sessionId,
            long startNs,
            long heapUsedStartBytes,
            AtomicLong maxHeapUsedBytes,
            ScheduledExecutorService heapSampler,
            Long processCpuTimeStartNs,
            long gcCountStart,
            long gcTimeStartMs
    ) {
    }
}
