package com.hope.music.common.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Preserves the original standalone text-broadcast example, separate from account and music state. */
@Component
public class BroadcastWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    @Override public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        sessions.add(session);
        send(session, "服务器：连接已建立，你的ID是" + session.getId());
    }
    @Override protected void handleTextMessage(WebSocketSession sender, TextMessage message) {
        for (var session : sessions) {
            try { send(session, "来自" + sender.getId() + "的消息: " + message.getPayload()); }
            catch (IOException ex) { sessions.remove(session); try { session.close(); } catch (IOException ignored) { } }
        }
    }
    private void send(WebSocketSession session, String text) throws IOException {
        synchronized (session) { if (session.isOpen()) session.sendMessage(new TextMessage(text)); }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) throws IOException {
        sessions.remove(session); session.close(CloseStatus.SERVER_ERROR);
    }
}
