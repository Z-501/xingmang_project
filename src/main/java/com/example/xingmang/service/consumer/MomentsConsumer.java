package com.example.xingmang.service.consumer;

import com.alibaba.fastjson.JSONObject;
import com.example.xingmang.config.RocketMQConstant;
import com.example.xingmang.model.entity.UserMoment;
import com.example.xingmang.service.FollowingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * MomentsConsumer 是动态模块中的消息消费者
 * 负责监听动态发布事件，并在收到消息后将博主的新动态分发到粉丝的 Redis 时间线中。
 * 该类本质上承担的是“异步扩散执行者”的角色，是动态模块从“发布事件”走向“粉丝侧可见”的关键中间环节。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "xingmang.rocketmq.moments-consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMQConstant.TOPIC_MOMENTS,
        consumerGroup = RocketMQConstant.GROUP_MOMENTS
)

public class MomentsConsumer implements RocketMQListener<String> {

    @Autowired
    private FollowingService followingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_MOMENTS_PREFIX = "moments:timeline:";

    @Override
    public void onMessage(String message) {
        // 1. 解析消息体
        UserMoment userMoment = JSONObject.parseObject(message, UserMoment.class);
        // 空值保护，如果一个博主的粉丝数量是0，就不需要执行后面的流程
        if (userMoment == null || userMoment.getId() == null || userMoment.getUserId() == null) {
            return;
        }
        Long userId = userMoment.getUserId();
        Long momentId = userMoment.getId();
        log.info("Received moment publish event, authorId={}, momentId={}", userId, momentId);

        // 2. 幂等性逻辑：防止 MQ 重复投递导致粉丝看到重复动态，通过 ZSet 的自动去重特性实现
        try {
            // Feed 分发仅需要粉丝 ID，避免加载用户资料和互关状态
            List<Long> fanIds = followingService.getFanIds(userId);
            if (fanIds == null || fanIds.isEmpty()) {
                return;
            }
            // 使用 Redis Pipeline 批量更新粉丝时间线
            dispatchToFans(fanIds, momentId);

        } catch (Exception e) {
            // 捕获异常并记录日志，防止单次分发失败导致整个消费者挂掉
            log.error("Failed to dispatch moment to fan timelines, authorId={}, momentId={}", userId, momentId, e);
            // 抛出异常会让 RocketMQ 稍后重试投递
            throw e;
        }
    }

    /**
     * 高并发分发逻辑：使用 Redis Pipeline 批量推送到粉丝时间线
     */
    private void dispatchToFans(List<Long> fanIds, Long momentId) {
        // 定义 Redis Key 的统一前缀，格式：moments:timeline:{userId}
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long fanId : fanIds) {
                String key = REDIS_MOMENTS_PREFIX + fanId;

                // 序列化数据：将动态 ID 存入 ZSet
                // Score 使用当前时间戳，保证时间线顺序
                byte[] rawKey = key.getBytes();
                byte[] rawValue = String.valueOf(momentId).getBytes();
                double score = System.currentTimeMillis();

                // 将指令塞入流水线（注意：此时并未真正发送给 Redis）
                connection.zAdd(rawKey, score, rawValue);

                // 限制每个粉丝的 Timeline 长度（例如只保留最近 500 条）
                // 防止某些僵尸号堆积大量过期动态占用内存
                connection.zRemRange(rawKey, 0, -501);
            }
            return null; // Pipeline 模式下返回值由 executePipelined 统一处理
        });
        log.info("Moment dispatched to fan timelines, fanCount={}, momentId={}", fanIds.size(), momentId);
    }
}
