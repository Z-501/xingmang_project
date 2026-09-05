package com.example.xingmang.benchmark.danmu;

import com.example.xingmang.exception.ConditionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BenchmarkDanmuMetrics {

    public static final String MODE_ASYNC = "async";
    public static final String MODE_SYNC = "sync";

    private final Object lock = new Object();
    private ActiveRun activeRun;

    public Map<String, Object> start(String runId, String mode) {
        String normalizedMode = normalizeMode(mode);
        if (!StringUtils.hasText(runId)) {
            throw new ConditionException(400, "runId 不能为空");
        }

        synchronized (lock) {
            if (activeRun != null) {
                throw new ConditionException(400, "已有 active Danmu Benchmark run");
            }
            activeRun = new ActiveRun(runId, normalizedMode);

            Map<String, Object> result = new HashMap<>();
            result.put("runId", runId);
            result.put("mode", normalizedMode);
            return result;
        }
    }

    public Map<String, Object> stop(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new ConditionException(400, "runId 不能为空");
        }

        synchronized (lock) {
            if (activeRun == null) {
                throw new ConditionException(400, "当前没有 active Danmu Benchmark run");
            }
            if (!activeRun.runId.equals(runId)) {
                throw new ConditionException(400, "runId 不匹配");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("runId", activeRun.runId);
            result.put("mode", activeRun.mode);
            result.put("messageCount", activeRun.records.size());
            result.put("handlerTotal", summarize(activeRun.records, MetricRecord::handlerTotalMs));
            result.put("broadcast", summarize(activeRun.records, MetricRecord::broadcastMs));
            result.put("redisCache", summarize(activeRun.records, MetricRecord::redisCacheMs));
            result.put("persistStage", summarize(activeRun.records, MetricRecord::persistStageMs));

            activeRun = null;
            return result;
        }
    }

    public BenchmarkMessage parseBenchmarkMessage(String content) {
        if (!StringUtils.hasText(content) || !content.startsWith("BM:")) {
            return null;
        }

        String[] parts = content.split(":", 3);
        if (parts.length != 3 || !StringUtils.hasText(parts[1]) || !StringUtils.hasText(parts[2])) {
            return null;
        }

        try {
            return new BenchmarkMessage(parts[1], Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean shouldRecord(String mode, BenchmarkMessage message) {
        synchronized (lock) {
            return activeRun != null
                    && activeRun.mode.equals(normalizeMode(mode))
                    && message != null
                    && activeRun.runId.equals(message.runId());
        }
    }

    public void record(String mode,
                       BenchmarkMessage message,
                       double handlerTotalMs,
                       double broadcastMs,
                       double redisCacheMs,
                       double persistStageMs) {
        synchronized (lock) {
            if (activeRun == null
                    || message == null
                    || !activeRun.runId.equals(message.runId())
                    || !activeRun.mode.equals(normalizeMode(mode))) {
                return;
            }

            activeRun.records.add(new MetricRecord(
                    activeRun.mode,
                    message.runId(),
                    message.sequence(),
                    handlerTotalMs,
                    broadcastMs,
                    redisCacheMs,
                    persistStageMs
            ));
        }
    }

    public String normalizeMode(String mode) {
        if (MODE_SYNC.equalsIgnoreCase(mode)) {
            return MODE_SYNC;
        }
        return MODE_ASYNC;
    }

    private Map<String, Object> summarize(List<MetricRecord> records, MetricValueExtractor extractor) {
        List<Double> values = new ArrayList<>();
        for (MetricRecord record : records) {
            values.add(extractor.value(record));
        }
        values.sort(Double::compareTo);

        Map<String, Object> result = new HashMap<>();
        result.put("avg", average(values));
        result.put("p50", percentile(values, 50));
        result.put("p95", percentile(values, 95));
        result.put("p99", percentile(values, 99));
        return result;
    }

    private Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (Double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private Double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.get(0);
        }

        double position = (values.size() - 1) * percentile / 100.0;
        int lower = (int) Math.floor(position);
        int upper = Math.min(lower + 1, values.size() - 1);
        double weight = position - lower;
        return values.get(lower) * (1.0 - weight) + values.get(upper) * weight;
    }

    @FunctionalInterface
    private interface MetricValueExtractor {
        double value(MetricRecord record);
    }

    private static final class ActiveRun {
        private final String runId;
        private final String mode;
        private final List<MetricRecord> records = new ArrayList<>();

        private ActiveRun(String runId, String mode) {
            this.runId = runId;
            this.mode = mode;
        }
    }

    public record BenchmarkMessage(String runId, int sequence) {
    }

    public record MetricRecord(
            String mode,
            String runId,
            int sequence,
            double handlerTotalMs,
            double broadcastMs,
            double redisCacheMs,
            double persistStageMs
    ) {
    }
}
