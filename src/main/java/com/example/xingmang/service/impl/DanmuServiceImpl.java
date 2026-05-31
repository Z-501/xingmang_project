package com.example.xingmang.service.impl;

import com.example.xingmang.util.DanmuRedisConstant;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.DanmuMapper;
import com.example.xingmang.model.entity.DanmuEntity;
import com.example.xingmang.model.vo.DanmuMessageVO;
import com.example.xingmang.service.DanmuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DanmuServiceImpl implements DanmuService {

    private static final int DANMU_HISTORY_DEFAULT_LIMIT = 200;
    private static final int DANMU_HISTORY_MAX_LIMIT = 300;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DanmuMapper danmuMapper;

    @Override
    public void cacheRecentDanmu(DanmuMessageVO danmuMessageVO) {
        if (danmuMessageVO == null || danmuMessageVO.getVideoId() == null) {
            return;
        }

        try {
            String key = DanmuRedisConstant.buildRecentDanmuKey(danmuMessageVO.getVideoId());
            String payload = objectMapper.writeValueAsString(danmuMessageVO);
            long score = toEpochMilli(danmuMessageVO.getCreateTime());

            // 1. 写入 ZSet
            stringRedisTemplate.opsForZSet().add(key, payload, score);

            // 2. 设置过期时间
            stringRedisTemplate.expire(key, Duration.ofHours(DanmuRedisConstant.DANMU_RECENT_TTL_HOURS));

            // 3. 控制最大缓存条数，超过后移除最旧的
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size != null && size > DanmuRedisConstant.DANMU_RECENT_MAX_COUNT) {
                long removeCount = size - DanmuRedisConstant.DANMU_RECENT_MAX_COUNT;
                stringRedisTemplate.opsForZSet().removeRange(key, 0, removeCount - 1);
            }
        } catch (Exception e) {
            log.warn("Failed to cache recent danmu, videoId={}", danmuMessageVO.getVideoId(), e);
        }
    }

    @Override
    public List<DanmuMessageVO> listRecentDanmus(Long videoId, Integer limit) {
        if (videoId == null || videoId <= 0) {
            return Collections.emptyList();
        }

        int finalLimit = normalizeRecentLimit(limit);
        String key = DanmuRedisConstant.buildRecentDanmuKey(videoId);

        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        if (size == null || size <= 0) {
            return Collections.emptyList();
        }

        long end = size - 1;
        long start = Math.max(0, size - finalLimit);

        Set<String> payloadSet = stringRedisTemplate.opsForZSet().range(key, start, end);
        if (payloadSet == null || payloadSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<DanmuMessageVO> result = new ArrayList<>();
        for (String payload : payloadSet) {
            try {
                DanmuMessageVO vo = objectMapper.readValue(payload, DanmuMessageVO.class);
                result.add(vo);
            } catch (Exception e) {
                log.warn("Failed to parse danmu payload from Redis");
            }
        }

        return result;
    }

    @Override
    public List<DanmuMessageVO> listInitDanmus(Long videoId, Integer limit) {
        if (videoId == null || videoId <= 0) {
            return Collections.emptyList();
        }

        int finalLimit = normalizeRecentLimit(limit);

        // 1. 先查 Redis 近期缓存
        List<DanmuMessageVO> redisDanmus = listRecentDanmus(videoId, finalLimit);
        if (redisDanmus.size() >= finalLimit) {
            return trimLast(redisDanmus, finalLimit);
        }

        // 2. Redis 不够，再查 MySQL 最近落库的弹幕
        List<DanmuEntity> latestDanmuEntities = danmuMapper.selectLatestDanmus(videoId, finalLimit);
        if (latestDanmuEntities == null || latestDanmuEntities.isEmpty()) {
            return redisDanmus;
        }

        // MySQL 查出来是倒序，这里转成正序，便于播放器初始化按时间先后渲染
        List<DanmuMessageVO> dbDanmus = latestDanmuEntities.stream()
                .sorted(Comparator.comparing(DanmuEntity::getCreateTime).thenComparing(DanmuEntity::getId))
                .map(this::convertEntityToVO)
                .toList();

        // 3. 合并去重，最终保留最近 finalLimit 条
        return mergeDanmusForInit(dbDanmus, redisDanmus, finalLimit);
    }

    @Override
    public List<DanmuMessageVO> listHistoryDanmus(Long videoId, Double from, Double to, Integer limit) {
        if (videoId == null || videoId <= 0) {
            throw new ConditionException(400, "videoId 不合法");
        }
        if (from == null || from < 0) {
            throw new ConditionException(400, "from 参数不合法");
        }
        if (to == null || to < from) {
            throw new ConditionException(400, "to 参数不合法");
        }

        int finalLimit = normalizeHistoryLimit(limit);

        List<DanmuEntity> danmuEntities = danmuMapper.selectHistoryDanmusByTimeRange(
                videoId,
                BigDecimal.valueOf(from),
                BigDecimal.valueOf(to),
                finalLimit
        );

        if (danmuEntities == null || danmuEntities.isEmpty()) {
            return Collections.emptyList();
        }

        return danmuEntities.stream()
                .map(this::convertEntityToVO)
                .toList();
    }

    private DanmuMessageVO convertEntityToVO(DanmuEntity entity) {
        return DanmuMessageVO.builder()
                .videoId(entity.getVideoId())
                .userId(entity.getUserId())
                .content(entity.getContent())
                .danmuTime(entity.getDanmuTime() == null ? null : entity.getDanmuTime().doubleValue())
                .color(entity.getColor())
                .mode(entity.getMode())
                .fontSize(entity.getFontSize())
                .createTime(entity.getCreateTime())
                .build();
    }

    private List<DanmuMessageVO> mergeDanmusForInit(List<DanmuMessageVO> dbDanmus,
                                                    List<DanmuMessageVO> redisDanmus,
                                                    int limit) {
        LinkedHashMap<String, DanmuMessageVO> mergedMap = new LinkedHashMap<>();

        // 先放 MySQL 较旧到较新的最近弹幕
        for (DanmuMessageVO vo : dbDanmus) {
            mergedMap.put(buildDanmuDedupKey(vo), vo);
        }

        // 再放 Redis 近期弹幕，覆盖同 key 内容
        for (DanmuMessageVO vo : redisDanmus) {
            mergedMap.put(buildDanmuDedupKey(vo), vo);
        }

        List<DanmuMessageVO> mergedList = new ArrayList<>(mergedMap.values());

        // 如果超过 limit，只保留最后 limit 条
        if (mergedList.size() > limit) {
            return mergedList.subList(mergedList.size() - limit, mergedList.size());
        }

        return mergedList;
    }

    private String buildDanmuDedupKey(DanmuMessageVO vo) {
        return String.valueOf(vo.getUserId()) + "_"
                + String.valueOf(vo.getDanmuTime()) + "_"
                + String.valueOf(vo.getCreateTime()) + "_"
                + String.valueOf(vo.getContent());
    }

    private int normalizeRecentLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DanmuRedisConstant.DANMU_RECENT_DEFAULT_LIMIT;
        }
        return Math.min(limit, DanmuRedisConstant.DANMU_RECENT_MAX_LIMIT);
    }

    private int normalizeHistoryLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DANMU_HISTORY_DEFAULT_LIMIT;
        }
        return Math.min(limit, DANMU_HISTORY_MAX_LIMIT);
    }

    private List<DanmuMessageVO> trimLast(List<DanmuMessageVO> list, int limit) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        if (list.size() <= limit) {
            return list;
        }
        return list.subList(list.size() - limit, list.size());
    }

    private long toEpochMilli(LocalDateTime time) {
        LocalDateTime safeTime = (time == null ? LocalDateTime.now() : time);
        return safeTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}