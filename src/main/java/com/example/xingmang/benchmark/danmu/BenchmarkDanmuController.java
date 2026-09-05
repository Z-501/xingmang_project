package com.example.xingmang.benchmark.danmu;

import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.BenchmarkDanmuMapper;
import com.example.xingmang.model.vo.DanmuMessageVO;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.util.DanmuRedisConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/benchmark/danmu")
@RequiredArgsConstructor
public class BenchmarkDanmuController {

    private final BenchmarkDanmuMetrics metrics;
    private final BenchmarkDanmuMapper benchmarkDanmuMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/metrics/start")
    public Result<Map<String, Object>> metricsStart(@RequestBody Map<String, String> body) {
        String runId = body == null ? null : body.get("runId");
        String mode = body == null ? null : body.get("mode");
        return Result.success(metrics.start(runId, mode));
    }

    @PostMapping("/metrics/stop")
    public Result<Map<String, Object>> metricsStop(@RequestBody Map<String, String> body) {
        String runId = body == null ? null : body.get("runId");
        return Result.success(metrics.stop(runId));
    }

    @GetMapping("/persisted-count")
    public Result<Map<String, Object>> persistedCount(@RequestParam("videoId") Long videoId,
                                                      @RequestParam("runId") String runId) {
        validateVideoAndRun(videoId, runId);
        long count = benchmarkDanmuMapper.countPersisted(videoId, buildBenchmarkPrefix(runId));

        Map<String, Object> result = new HashMap<>();
        result.put("videoId", videoId);
        result.put("runId", runId);
        result.put("persistedCount", count);
        return Result.success(result);
    }

    @DeleteMapping("/data")
    public Result<Map<String, Object>> cleanup(@RequestParam("videoId") Long videoId,
                                               @RequestParam("runId") String runId) {
        validateVideoAndRun(videoId, runId);
        String prefix = buildBenchmarkPrefix(runId);
        int deletedMysqlRows = benchmarkDanmuMapper.deleteBenchmarkRows(videoId, prefix);
        long deletedRedisMembers = deleteRedisBenchmarkMembers(videoId, prefix);

        Map<String, Object> result = new HashMap<>();
        result.put("videoId", videoId);
        result.put("runId", runId);
        result.put("deletedMysqlRows", deletedMysqlRows);
        result.put("deletedRedisMembers", deletedRedisMembers);
        return Result.success(result);
    }

    private long deleteRedisBenchmarkMembers(Long videoId, String prefix) {
        String key = DanmuRedisConstant.buildRecentDanmuKey(videoId);
        Set<String> members = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        if (members == null || members.isEmpty()) {
            return 0L;
        }

        Set<String> targetMembers = new HashSet<>();
        for (String member : members) {
            try {
                DanmuMessageVO message = objectMapper.readValue(member, DanmuMessageVO.class);
                if (message != null
                        && StringUtils.hasText(message.getContent())
                        && message.getContent().startsWith(prefix)) {
                    targetMembers.add(member);
                }
            } catch (Exception ignored) {
                // 非本次 Benchmark JSON 不处理，避免误删真实数据。
            }
        }

        if (targetMembers.isEmpty()) {
            return 0L;
        }

        Long removed = stringRedisTemplate.opsForZSet().remove(key, targetMembers.toArray());
        return removed == null ? 0L : removed;
    }

    private void validateVideoAndRun(Long videoId, String runId) {
        if (videoId == null || videoId <= 0) {
            throw new ConditionException(400, "videoId 不合法");
        }
        if (!StringUtils.hasText(runId) || runId.contains("%") || runId.contains("_") || runId.contains(":")) {
            throw new ConditionException(400, "runId 不合法");
        }
    }

    private String buildBenchmarkPrefix(String runId) {
        return "BM:" + runId + ":";
    }
}
