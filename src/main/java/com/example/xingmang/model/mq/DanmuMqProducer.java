package com.example.xingmang.model.mq;

import com.example.xingmang.config.RocketMQConstant;
import com.example.xingmang.model.message.DanmuPersistMessage;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DanmuMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送弹幕持久化消息
     */
    public void sendDanmuPersistMessage(DanmuPersistMessage message) {
        rocketMQTemplate.convertAndSend(RocketMQConstant.TOPIC_DANMU_PERSIST, message);
    }
}
