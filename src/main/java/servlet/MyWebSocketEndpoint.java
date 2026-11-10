package servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// 端点路径：客户端将通过此路径连接
@ServerEndpoint("/websocket")
public class MyWebSocketEndpoint {
    private static final Logger log = LoggerFactory.getLogger(MyWebSocketEndpoint.class);

    // 线程安全的集合，存储所有活跃连接
    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    // 客户端连接建立时触发
    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        log.info("客户端连接成功，会话ID: {}", session.getId());
        try {
            // 主动向客户端发送连接成功消息（便于测试）
            session.getBasicRemote().sendText("服务器：连接已建立，你的ID是" + session.getId());
        } catch (IOException e) {
            log.error("WebSocket发送连接确认消息失败: sessionId={}", session.getId(), e);
        }
    }

    // 接收客户端消息时触发
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到消息: {} 来自 {}", message, session.getId());
        // 广播消息给所有客户端
        for (Session s : sessions) {
            if (s.isOpen()) {
                try {
                    s.getBasicRemote().sendText("来自" + session.getId() + "的消息: " + message);
                } catch (IOException e) {
                    log.error("WebSocket广播消息失败: toSessionId={}", s.getId(), e);
                }
            }
        }
    }

    // 连接关闭时触发
    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        log.info("WebSocket连接关闭: sessionId={}", session.getId());
    }

    // 错误处理
    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket错误: sessionId={}, error={}", session.getId(), throwable.getMessage(), throwable);
    }
}