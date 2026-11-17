package com.hope.music.common.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/** Same-origin standalone broadcast endpoint; new clients can use the proxied API path. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final BroadcastWebSocketHandler handler;
    public WebSocketConfig(BroadcastWebSocketHandler handler) { this.handler = handler; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/websocket", "/api/v1/websocket");
    }
}
