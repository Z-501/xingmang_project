package com.example.xingmang.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

/**
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmuSessionContext {

    /**
     * 当前连接对应的用户ID
     */
    private Long userId;

    /**
     * 当前连接所在的视频房间ID
     */
    private Long videoId;

    /**
     * 当前连接的 WebSocketSession
     */
    private WebSocketSession session;
}
