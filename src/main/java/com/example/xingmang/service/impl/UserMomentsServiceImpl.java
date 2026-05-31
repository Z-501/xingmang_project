package com.example.xingmang.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.example.xingmang.config.RocketMQConstant;
import com.example.xingmang.mapper.UserMomentsMapper;
import com.example.xingmang.model.entity.UserMoment;
import com.example.xingmang.service.UserMomentsService;
import com.example.xingmang.util.RocketMQUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates moment publishing and feed retrieval.
 * It persists moments in MySQL, triggers RocketMQ fan-out after transaction commit,
 * and reads follower timelines from Redis with batched database hydration.
 */
@Slf4j
@Service
public class UserMomentsServiceImpl implements UserMomentsService {

    @Autowired
    private UserMomentsMapper userMomentsMapper;

    @Autowired
    private RocketMQUtil rocketMQUtil;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_MOMENTS_PREFIX = "moments:timeline:";

    /**
     * Persist a moment and trigger asynchronous fan-out.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUserMoments(UserMoment userMoment) {
        // 1. 持久化落库：确保数据不丢失
        userMoment.setCreateTime(new Date());
        userMomentsMapper.insert(userMoment);

        // Serialize the persisted moment and send the fan-out event only after the database transaction commits.
        String payload = JSONObject.toJSONString(userMoment);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Register an after-commit hook to avoid publishing events for rolled-back data.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rocketMQUtil.asyncSend(RocketMQConstant.TOPIC_MOMENTS, payload);
                    log.info("Moment publish transaction committed, MQ fan-out event sent, userId={}", userMoment.getUserId());
                }
            });
        } else {
            rocketMQUtil.asyncSend(RocketMQConstant.TOPIC_MOMENTS, payload);
        }
        log.info("Moment persisted and fan-out event scheduled, userId={}, momentId={}", userMoment.getUserId(), userMoment.getId());
    }

    /**
     * Read the current user feed with cursor-based pagination.
     */
    @Override
    public List<UserMoment> getUserMoments(Long userId, Integer size, Long lastTime) {
        String key = REDIS_MOMENTS_PREFIX + userId;
        // Use timestamp score as the pagination cursor.
        double max = (lastTime == null || lastTime == 0) ? System.currentTimeMillis() : lastTime - 1;
        // Read moment IDs from Redis in reverse chronological order.
        Set<String> idSet = redisTemplate.opsForZSet().reverseRangeByScore(key, 0, max, 0, size);
        if (idSet == null || idSet.isEmpty()) {
            
            return new ArrayList<>();
        }
        // Convert Redis string values to database IDs.
        List<Long> ids = idSet.stream().map(Long::valueOf).collect(Collectors.toList());

        // Hydrate timeline entries from MySQL in batch.
        List<UserMoment> moments = userMomentsMapper.selectBatchIds(ids);

        // Preserve Redis timeline order after batch database retrieval.
        Map<Long, UserMoment> momentMap = moments.stream()
                .collect(Collectors.toMap(UserMoment::getId, m -> m));

        return ids.stream()
                .map(momentMap::get)
                // Filter entries that may have been deleted from the database.
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
