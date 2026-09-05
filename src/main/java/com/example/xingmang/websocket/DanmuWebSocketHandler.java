package com.example.xingmang.websocket;

import com.example.xingmang.benchmark.danmu.BenchmarkDanmuMetrics;
import com.example.xingmang.benchmark.danmu.BenchmarkDanmuSyncPersistence;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.model.dto.DanmuSendDTO;
import com.example.xingmang.model.message.DanmuPersistMessage;
import com.example.xingmang.model.mq.DanmuMqProducer;
import com.example.xingmang.model.vo.DanmuMessageVO;
import com.example.xingmang.service.DanmuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * WebSocket danmu handler.
 *
 * The benchmark branch keeps validation, broadcast and Redis cache order identical
 * in both modes and changes only the persistence stage:
 * async -> RocketMQ producer handoff; sync -> current handler thread inserts MySQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmuWebSocketHandler extends TextWebSocketHandler {

    private final DanmuRoomManager danmuRoomManager;
    private final ObjectMapper objectMapper;
    private final DanmuService danmuService;
    private final DanmuMqProducer danmuMqProducer;
    private final BenchmarkDanmuSyncPersistence benchmarkDanmuSyncPersistence;
    private final BenchmarkDanmuMetrics benchmarkDanmuMetrics;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        Long videoId = getVideoId(session);

        danmuRoomManager.joinRoom(videoId, userId, session);

        log.info("Danmu WebSocket connected, userId={}, videoId={}, onlineCount={}",
                userId, videoId, danmuRoomManager.getRoomOnlineCount(videoId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        long handlerStartNs = System.nanoTime();
        Long userId = getUserId(session);
        Long videoId = getVideoId(session);
        String benchmarkPersistMode = getBenchmarkPersistMode(session);

        DanmuSendDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), DanmuSendDTO.class);
        } catch (Exception e) {
            throw new ConditionException(400, "弹幕消息格式不正确");
        }

        validateDanmu(dto);

        DanmuMessageVO danmuMessage = DanmuMessageVO.builder()
                .videoId(videoId)
                .userId(userId)
                .content(dto.getContent().trim())
                .danmuTime(dto.getDanmuTime())
                .color(normalizeColor(dto.getColor()))
                .mode(normalizeMode(dto.getMode()))
                .fontSize(normalizeFontSize(dto.getFontSize()))
                .createTime(LocalDateTime.now())
                .build();

        BenchmarkDanmuMetrics.BenchmarkMessage benchmarkMessage =
                benchmarkDanmuMetrics.parseBenchmarkMessage(danmuMessage.getContent());
        boolean recordBenchmark =
                benchmarkDanmuMetrics.shouldRecord(benchmarkPersistMode, benchmarkMessage);

        long broadcastStartNs = System.nanoTime();
        broadcastToRoom(videoId, danmuMessage);
        double broadcastMs = nsToMs(System.nanoTime() - broadcastStartNs);

        long redisStartNs = System.nanoTime();
        danmuService.cacheRecentDanmu(danmuMessage);
        double redisCacheMs = nsToMs(System.nanoTime() - redisStartNs);

        long persistStartNs = System.nanoTime();
        if (BenchmarkDanmuMetrics.MODE_SYNC.equals(benchmarkPersistMode)) {
            benchmarkDanmuSyncPersistence.persist(danmuMessage);
        } else {
            DanmuPersistMessage persistMessage = new DanmuPersistMessage();
            persistMessage.setVideoId(danmuMessage.getVideoId());
            persistMessage.setUserId(danmuMessage.getUserId());
            persistMessage.setContent(danmuMessage.getContent());
            persistMessage.setDanmuTime(danmuMessage.getDanmuTime());
            persistMessage.setColor(danmuMessage.getColor());
            persistMessage.setMode(danmuMessage.getMode());
            persistMessage.setFontSize(danmuMessage.getFontSize());
            persistMessage.setCreateTime(danmuMessage.getCreateTime());

            try {
                danmuMqProducer.sendDanmuPersistMessage(persistMessage);
            } catch (Exception e) {
                log.warn("Failed to send danmu persistence message, videoId={}, userId={}", videoId, userId, e);
            }
        }
        double persistStageMs = nsToMs(System.nanoTime() - persistStartNs);

        if (recordBenchmark) {
            benchmarkDanmuMetrics.record(
                    benchmarkPersistMode,
                    benchmarkMessage,
                    nsToMs(System.nanoTime() - handlerStartNs),
                    broadcastMs,
                    redisCacheMs,
                    persistStageMs
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long videoId = getVideoIdSafely(session);
        danmuRoomManager.leaveRoom(session);

        log.info("Danmu WebSocket closed, sessionId={}, videoId={}, onlineCount={}",
                session.getId(), videoId, videoId == null ? 0 : danmuRoomManager.getRoomOnlineCount(videoId));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } finally {
            danmuRoomManager.leaveRoom(session);
        }
    }

    private void broadcastToRoom(Long videoId, DanmuMessageVO danmuMessage) throws Exception {
        Collection<DanmuSessionContext> roomSessions = danmuRoomManager.getRoomSessions(videoId);
        if (roomSessions.isEmpty()) {
            return;
        }

        String payload = objectMapper.writeValueAsString(danmuMessage);
        TextMessage textMessage = new TextMessage(payload);

        for (DanmuSessionContext context : roomSessions) {
            WebSocketSession targetSession = context.getSession();
            if (targetSession != null && targetSession.isOpen()) {
                targetSession.sendMessage(textMessage);
            }
        }
    }

    private void validateDanmu(DanmuSendDTO dto) {
        if (dto == null) {
            throw new ConditionException(400, "弹幕消息不能为空");
        }

        if (!StringUtils.hasText(dto.getContent())) {
            throw new ConditionException(400, "弹幕内容不能为空");
        }

        String content = dto.getContent().trim();
        if (content.length() > 100) {
            throw new ConditionException(400, "弹幕内容不能超过100个字符");
        }

        if (dto.getDanmuTime() == null || dto.getDanmuTime() < 0) {
            throw new ConditionException(400, "弹幕时间位置不合法");
        }
    }

    private String normalizeColor(String color) {
        if (!StringUtils.hasText(color)) {
            return "#FFFFFF";
        }
        return color.trim();
    }

    private Integer normalizeMode(Integer mode) {
        if (mode == null || mode < 1 || mode > 3) {
            return 1;
        }
        return mode;
    }

    private Integer normalizeFontSize(Integer fontSize) {
        if (fontSize == null || fontSize < 1 || fontSize > 3) {
            return 2;
        }
        return fontSize;
    }

    private Long getUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(DanmuHandshakeInterceptor.ATTR_USER_ID);
        if (!(value instanceof Long userId)) {
            throw new ConditionException(401, "弹幕连接未识别到用户信息");
        }
        return userId;
    }

    private Long getVideoId(WebSocketSession session) {
        Object value = session.getAttributes().get(DanmuHandshakeInterceptor.ATTR_VIDEO_ID);
        if (!(value instanceof Long videoId)) {
            throw new ConditionException(400, "弹幕连接未识别到视频房间");
        }
        return videoId;
    }

    private Long getVideoIdSafely(WebSocketSession session) {
        Object value = session.getAttributes().get(DanmuHandshakeInterceptor.ATTR_VIDEO_ID);
        return value instanceof Long ? (Long) value : null;
    }

    private String getBenchmarkPersistMode(WebSocketSession session) {
        Object value = session.getAttributes().get(DanmuHandshakeInterceptor.ATTR_BENCHMARK_PERSIST_MODE);
        return benchmarkDanmuMetrics.normalizeMode(value instanceof String mode ? mode : null);
    }

    private double nsToMs(long ns) {
        return ns / 1_000_000.0;
    }
}
