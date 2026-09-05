package com.example.xingmang.benchmark.danmu;

import com.example.xingmang.mapper.DanmuMapper;
import com.example.xingmang.model.entity.DanmuEntity;
import com.example.xingmang.model.vo.DanmuMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class BenchmarkDanmuSyncPersistence {

    private final DanmuMapper danmuMapper;

    public void persist(DanmuMessageVO message) {
        if (message == null || message.getVideoId() == null || message.getUserId() == null) {
            return;
        }

        DanmuEntity entity = new DanmuEntity();
        entity.setVideoId(message.getVideoId());
        entity.setUserId(message.getUserId());
        entity.setContent(message.getContent());
        entity.setDanmuTime(
                message.getDanmuTime() == null
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(message.getDanmuTime())
        );
        entity.setColor(message.getColor());
        entity.setMode(message.getMode());
        entity.setFontSize(message.getFontSize());
        entity.setStatus(1);
        entity.setCreateTime(
                message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime()
        );

        danmuMapper.insert(entity);
    }
}
