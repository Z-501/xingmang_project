package com.example.xingmang.service.consumer;

import com.alibaba.fastjson.JSONObject;
import com.example.xingmang.config.RocketMQConstant;
import com.example.xingmang.model.entity.UserMoment;
import com.example.xingmang.service.FollowingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Feed fan-out consumer.
 *
 * This benchmark branch adds one controlled switch for Redis dispatch:
 * pipeline (production path) vs single (same callback through RedisTemplate.execute).
 * Fan ID lookup, Redis keys, ZADD/ZREMRANGE operations and MQ flow stay unchanged.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "xingmang.rocketmq.moments-consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMQConstant.TOPIC_MOMENTS,
        consumerGroup = RocketMQConstant.GROUP_MOMENTS
)
public class MomentsConsumer implements RocketMQListener<String> {

    private static final String REDIS_MOMENTS_PREFIX = "moments:timeline:";
    private static final String REDIS_MODE_SINGLE = "single";

    @Autowired
    private FollowingService followingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${xingmang.benchmark.feed.redis-mode:pipeline}")
    private String redisMode;

    @Override
    public void onMessage(String message) {
        UserMoment userMoment = JSONObject.parseObject(message, UserMoment.class);
        if (userMoment == null || userMoment.getId() == null || userMoment.getUserId() == null) {
            return;
        }

        Long userId = userMoment.getUserId();
        Long momentId = userMoment.getId();
        log.info("Received moment publish event, authorId={}, momentId={}", userId, momentId);

        try {
            List<Long> fanIds = followingService.getFanIds(userId);
            if (fanIds == null || fanIds.isEmpty()) {
                return;
            }

            dispatchToFans(fanIds, momentId);
        } catch (Exception e) {
            log.error("Failed to dispatch moment to fan timelines, authorId={}, momentId={}", userId, momentId, e);
            throw e;
        }
    }

    private void dispatchToFans(List<Long> fanIds, Long momentId) {
        RedisCallback<Object> callback = connection -> {
            for (Long fanId : fanIds) {
                String key = REDIS_MOMENTS_PREFIX + fanId;
                byte[] rawKey = key.getBytes();
                byte[] rawValue = String.valueOf(momentId).getBytes();
                double score = System.currentTimeMillis();

                connection.zAdd(rawKey, score, rawValue);
                connection.zRemRange(rawKey, 0, -501);
            }
            return null;
        };

        if (REDIS_MODE_SINGLE.equalsIgnoreCase(redisMode)) {
            redisTemplate.execute(callback);
        } else {
            redisTemplate.executePipelined(callback);
        }

        log.info("Moment dispatched to fan timelines, fanCount={}, momentId={}, redisMode={}",
                fanIds.size(), momentId, redisMode);
    }
}
