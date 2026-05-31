package com.example.xingmang.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DanmuRoomManager {

    /**
     * 房间映射：videoId -> (sessionId -> sessionContext)
     */
    private final Map<Long, Map<String, DanmuSessionContext>> roomSessionMap = new ConcurrentHashMap<>();

    /**
     * 单个 session 对应的上下文：sessionId -> sessionContext
     */
    private final Map<String, DanmuSessionContext> sessionContextMap = new ConcurrentHashMap<>();

    /**
     * 用户加入房间
     */
    public void joinRoom(Long videoId, Long userId, WebSocketSession session) {
        DanmuSessionContext context = new DanmuSessionContext(userId, videoId, session);

        roomSessionMap.computeIfAbsent(videoId, key -> new ConcurrentHashMap<>())
                .put(session.getId(), context);

        sessionContextMap.put(session.getId(), context);
    }

    /**
     * 用户离开房间
     */
    public void leaveRoom(WebSocketSession session) {
        if (session == null) {
            return;
        }

        DanmuSessionContext context = sessionContextMap.remove(session.getId());
        if (context == null) {
            return;
        }

        Map<String, DanmuSessionContext> room = roomSessionMap.get(context.getVideoId());
        if (room != null) {
            room.remove(session.getId());
            if (room.isEmpty()) {
                roomSessionMap.remove(context.getVideoId());
            }
        }
    }

    /**
     * 获取某个 session 的上下文
     */
    public DanmuSessionContext getSessionContext(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        return sessionContextMap.get(session.getId());
    }

    /**
     * 获取某个视频房间的所有连接
     */
    public Collection<DanmuSessionContext> getRoomSessions(Long videoId) {
        Map<String, DanmuSessionContext> room = roomSessionMap.get(videoId);
        if (room == null || room.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(room.values());
    }

    /**
     * 获取房间在线人数（可用于日志）
     */
    public int getRoomOnlineCount(Long videoId) {
        Map<String, DanmuSessionContext> room = roomSessionMap.get(videoId);
        return room == null ? 0 : room.size();
    }
}