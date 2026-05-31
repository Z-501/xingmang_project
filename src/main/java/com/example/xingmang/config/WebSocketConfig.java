package com.example.xingmang.config;

import com.example.xingmang.websocket.DanmuHandshakeInterceptor;
import com.example.xingmang.websocket.DanmuWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DanmuWebSocketHandler danmuWebSocketHandler;
    private final DanmuHandshakeInterceptor danmuHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(danmuWebSocketHandler, "/ws/danmu")
                .addInterceptors(danmuHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}