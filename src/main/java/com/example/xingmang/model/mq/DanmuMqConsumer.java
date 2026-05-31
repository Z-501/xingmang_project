package com.example.xingmang.model.mq;

import com.example.xingmang.config.RocketMQConstant;
import com.example.xingmang.mapper.DanmuMapper;
import com.example.xingmang.model.entity.DanmuEntity;
import com.example.xingmang.model.message.DanmuPersistMessage;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.TOPIC_DANMU_PERSIST,
        consumerGroup = RocketMQConstant.GROUP_DANMU_PERSIST,
        messageModel = MessageModel.CLUSTERING
)
public class DanmuMqConsumer implements RocketMQListener<DanmuPersistMessage> {

    private final DanmuMapper danmuMapper;

    @Override
    public void onMessage(DanmuPersistMessage message) {
        if (message == null || message.getVideoId() == null || message.getUserId() == null) {
            return;
        }

        DanmuEntity danmuEntity = new DanmuEntity();
        danmuEntity.setVideoId(message.getVideoId());
        danmuEntity.setUserId(message.getUserId());
        danmuEntity.setContent(message.getContent());
        danmuEntity.setDanmuTime(
                message.getDanmuTime() == null
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(message.getDanmuTime())
        );
        danmuEntity.setColor(message.getColor());
        danmuEntity.setMode(message.getMode());
        danmuEntity.setFontSize(message.getFontSize());
        danmuEntity.setStatus(1);
        danmuEntity.setCreateTime(
                message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime()
        );

        danmuMapper.insert(danmuEntity);
    }
}
