package com.example.xingmang.websocket;

import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.util.JwtUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Validates WebSocket handshake parameters and stores the authenticated user and room context.
 */
@Component
public class DanmuHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_VIDEO_ID = "videoId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            throw new ConditionException(400, "WebSocket 握手请求类型不正确");
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // Read access token from handshake query parameters.
        String accessToken = httpRequest.getParameter("accessToken");
        if (!StringUtils.hasText(accessToken)) {
            throw new ConditionException(401, "缺少 accessToken，无法建立弹幕连接");
        }

        // Validate token and resolve user identity.
        Long userId = JwtUtil.parseToken(accessToken);
        if (userId == null) {
            throw new ConditionException(401, "用户信息已过期，请重新登录");
        }

        // Validate target video room.
        String videoIdStr = httpRequest.getParameter("videoId");
        if (!StringUtils.hasText(videoIdStr)) {
            throw new ConditionException(400, "缺少 videoId，无法进入弹幕房间");
        }

        Long videoId;
        try {
            videoId = Long.parseLong(videoIdStr);
        } catch (NumberFormatException e) {
            throw new ConditionException(400, "videoId 格式不正确");
        }

        if (videoId <= 0) {
            throw new ConditionException(400, "videoId 不合法");
        }

        // Store resolved context for the WebSocket handler.
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_VIDEO_ID, videoId);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 前期不需要做额外处理
    }
}