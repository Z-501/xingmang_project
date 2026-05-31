package com.example.xingmang.util;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQUtil 是 RocketMQ 的工具封装类，负责统一封装消息发送逻辑
 * 降低业务层直接操作 RocketMQTemplate 的复杂度。
 * 该类本质上是消息发送的通用能力层
 * 为业务层提供同步发送、异步发送单条消息、异步发送多条消息等统一接口
 * 使 MQ 的接入方式更加规范和清晰。
 */
@Slf4j
@Component
public class RocketMQUtil {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发送消息
     */
    public void syncSend(String topic, Object payload) {
        Message<Object> message = MessageBuilder.withPayload(payload).build();
        rocketMQTemplate.syncSend(topic, message);
    }

    /**
     * 异步发送单条消息
     */
    public void asyncSend(String topic, Object payload) {
        Message<Object> message = MessageBuilder.withPayload(payload).build();
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("RocketMQ message sent successfully, msgId={}", sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable throwable) {
                log.warn("RocketMQ message delivery failed", throwable);
            }
        });
    }
    /**
     * 异步发送消息（用于社交动态分发，不阻塞主业务，发布多条消息）
     */
    public void asyncSend(String topic, List<Object> payloads) {
        // 1. 根据消息数量初始化计数器
        CountDownLatch latch = new CountDownLatch(payloads.size());
        for (Object payload : payloads) {
            Message<Object> message = MessageBuilder.withPayload(payload).build();
            rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 投递成功，计数减一
                    latch.countDown();
                    log.info("RocketMQ message sent successfully, msgId={}", sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    // 投递失败也必须 countDown，否则主线程会永远阻塞
                    latch.countDown();
                    log.warn("RocketMQ message delivery failed", throwable);
                }
            });
        }
        try {
            // 2. 设置超时时间,高并发下绝对不能无限等待
            boolean allSent = latch.await(5, TimeUnit.SECONDS);
            if (!allSent) {
                log.warn("RocketMQ batch delivery timed out before all callbacks completed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
