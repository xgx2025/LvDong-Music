package com.hope.music.provider.service;

import com.hope.music.provider.dao.QqMusicConnectionDao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** User-owned QR lifecycle and encrypted account binding for a single Boot instance. */
@Service
public class QqMusicConnectionService {
    private static final long QR_TTL_MS = 180_000;
    private static final int MAX_TASKS = 256;
    private final QqMusicClient client;
    private final MusicTokenCipher cipher;
    private final QqMusicConnectionDao dao;
    private final Map<String, QrTask> tasks = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[64];

    public QqMusicConnectionService(QqMusicClient client, MusicTokenCipher cipher, QqMusicConnectionDao dao) {
        this.client = client; this.cipher = cipher; this.dao = dao;
        Arrays.setAll(locks, i -> new Object());
    }

    public Capabilities capabilities() {
        if (!configured()) return new Capabilities(false, List.of());
        JsonNode data = client.get("/login/channels", Map.of(), null).path("data");
        // This integration relies on revocable stored sessions, not serverless sealed tokens.
        if (!data.path("sessionMode").asString("").equals("stored")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请配置常驻 QQ 音乐服务");
        }
        List<String> channels = new ArrayList<>();
        for (JsonNode node : data.path("channels")) {
            String channel = node.asString("");
            if (List.of("qqmusic", "qq", "wechat").contains(channel)) channels.add(channel);
        }
        return new Capabilities(true, channels.contains("qqmusic") ? List.of("qqmusic") : List.copyOf(channels));
    }

    public ConnectionStatus status(String userId) {
        if (!configured()) return new ConnectionStatus(false, false, false, null, null);
        synchronized (lock(userId)) {
            var binding = dao.find(userId);
            if (binding == null) return disconnected();
            String token;
            try { token = cipher.decrypt(userId, binding.encryptedToken()); }
            catch (RuntimeException e) { return new ConnectionStatus(true, false, true, binding.nickname(), binding.connectedAt()); }
            try {
                JsonNode profile = client.get("/login/status", Map.of(), token).path("data").path("profile");
                if (accountId(profile).equals(binding.accountId())) {
                    return new ConnectionStatus(true, true, false, binding.nickname(), binding.connectedAt());
                }
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 409) throw e;
            }
            // Keep expired binding until explicit disconnect/reconnect; transient failures do not erase it.
            return new ConnectionStatus(true, false, true, binding.nickname(), binding.connectedAt());
        }
    }

    public QrView start(String userId, String channel) {
        requireConfigured();
        synchronized (lock(userId)) {
            if (!capabilities().channels().contains(channel)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该扫码方式暂不可用");
            }
            var oldTask = tasks.get(userId);
            if (oldTask != null && System.currentTimeMillis() - oldTask.startedAt < 5_000) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请稍后刷新二维码");
            }
            removeTask(userId);
            QrTask task = new QrTask(channel);
            synchronized (tasks) {
                long now = System.currentTimeMillis();
                tasks.entrySet().removeIf(e -> e.getValue().expiresAt < now);
                if (tasks.size() >= MAX_TASKS) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "扫码人数较多，请稍后再试");
                tasks.put(userId, task);
            }
            try {
                JsonNode key = client.get("/login/qr/key", Map.of("channel", channel), null);
                task.upstreamKey = text(key.path("data"), "unikey", 20_000);
                if (task.upstreamKey.isEmpty()) throw unavailable();
                task.image = text(client.get("/login/qr/create", Map.of("key", task.upstreamKey), null).path("data"), "qrimg", 900_000);
                // Only embedded PNG/JPEG images reach the browser; no external tracking URLs or SVG.
                if (!task.image.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=\\r\\n]+")) throw unavailable();
                if (task.cancelled || tasks.get(userId) != task) { client.cancelQuietly(task.upstreamKey); throw unavailable(); }
                return task.view();
            } catch (RuntimeException e) { removeTask(userId); throw e; }
        }
    }

    public QrView check(String userId, String id) {
        requireConfigured();
        synchronized (lock(userId)) {
            QrTask task = ownedTask(userId, id);
            if (!task.state.equals("waiting") && !task.state.equals("scanned")) return task.view();
            if (task.expiresAt <= System.currentTimeMillis()) {
                task.state = "expired"; client.cancelQuietly(task.upstreamKey); return task.view();
            }
            if (System.currentTimeMillis() - task.lastPoll < 1_500) return task.view();
            task.lastPoll = System.currentTimeMillis();
            if (task.token == null) {
                JsonNode result = client.get("/login/qr/check", Map.of("key", task.upstreamKey), null);
                switch (result.path("code").asInt()) {
                    case 801 -> task.state = "waiting";
                    case 802 -> task.state = "scanned";
                    case 800 -> {
                        task.state = "expired";
                        task.message = "二维码已失效或平台拒绝登录，请刷新后重试；设备已满时请先在 QQ 音乐管理登录设备";
                    }
                    case 803 -> {
                        String cookie = text(result, "cookie", 20_000);
                        for (String part : cookie.split(";")) {
                            String value = part.strip();
                            if (value.startsWith("qqmusic_session=")) task.token = value.substring("qqmusic_session=".length());
                        }
                        if (task.token == null || !task.token.matches("[A-Za-z0-9_.=-]{1,20000}")) throw unavailable();
                    }
                    default -> throw unavailable();
                }
            }
            if (task.token != null) {
                String token = task.token;
                try {
                    JsonNode profile = client.get("/login/status", Map.of(), token).path("data").path("profile");
                    String accountId = accountId(profile);
                    if (accountId.isEmpty()) throw unavailable();
                    String nickname = text(profile, "nickname", 200);
                    if (nickname.isBlank()) nickname = text(profile, "nick", 200);
                    if (nickname.isBlank()) nickname = "QQ 音乐用户";
                    var previous = dao.find(userId);
                    synchronized (task) {
                        if (task.cancelled || task.expiresAt <= System.currentTimeMillis() || tasks.get(userId) != task) {
                            task.state = "expired"; client.logoutQuietly(token); task.token = null; return task.view();
                        }
                        dao.save(userId, accountId, nickname, cipher.encrypt(userId, token));
                        task.state = "confirmed"; task.token = null;
                    }
                    if (previous != null) {
                        try {
                            String previousToken = cipher.decrypt(userId, previous.encryptedToken());
                            if (!previousToken.equals(token)) client.logoutQuietly(previousToken);
                        } catch (RuntimeException ignored) { }
                    }
                } catch (ResponseStatusException e) {
                    if (task.cancelled) { task.state = "expired"; task.token = null; return task.view(); }
                    if (e.getStatusCode().value() == 409) {
                        task.state = "expired"; task.token = null;
                    }
                    throw e;
                }
            }
            return task.view();
        }
    }

    public void cancel(String userId, String id) {
        QrTask task = tasks.get(userId);
        if (task != null && task.id.equals(id)) {
            synchronized (task) { task.cancelled = true; tasks.remove(userId, task); }
            cleanup(task);
        }
    }

    public void disconnect(String userId) {
        QrTask task = tasks.get(userId);
        if (task != null) cancel(userId, task.id);
        synchronized (lock(userId)) {
            removeTask(userId);
            var binding = dao.find(userId);
            dao.delete(userId);
            if (binding != null && cipher.configured()) {
                try { client.logoutQuietly(cipher.decrypt(userId, binding.encryptedToken())); }
                catch (RuntimeException ignored) { }
            }
        }
    }

    /** Run music operations against the current binding while excluding account changes. */
    public <T> T withSession(String userId, java.util.function.Function<MusicSession, T> operation) {
        requireConfigured();
        synchronized (lock(userId)) {
            var binding = dao.find(userId);
            if (binding == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先连接 QQ 音乐账号");
            String token;
            try { token = cipher.decrypt(userId, binding.encryptedToken()); }
            catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "QQ 音乐连接已失效，请重新连接"); }
            return operation.apply(new MusicSession(binding.accountId(), token));
        }
    }

    /** Server-only music context. Never return this record from a controller. */
    public record MusicSession(String accountId, String token) { }

    private void removeTask(String userId) {
        QrTask task = tasks.remove(userId);
        if (task != null) {
            synchronized (task) { task.cancelled = true; }
            cleanup(task);
        }
    }
    private void cleanup(QrTask task) {
        if (task.token != null) client.logoutQuietly(task.token);
        if (task.upstreamKey != null && !task.state.equals("confirmed")) client.cancelQuietly(task.upstreamKey);
    }
    private QrTask ownedTask(String userId, String id) {
        QrTask task = tasks.get(userId);
        if (task == null || !task.id.equals(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "扫码任务已结束，请重新连接");
        return task;
    }
    private Object lock(String userId) { return locks[Math.floorMod(userId.hashCode(), locks.length)]; }
    private boolean configured() { return client.configured() && cipher.configured(); }
    private void requireConfigured() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐连接服务尚未配置");
    }
    private static ConnectionStatus disconnected() { return new ConnectionStatus(true, false, false, null, null); }
    private static String accountId(JsonNode profile) {
        String id = text(profile, "str_musicid", 100);
        if (id.isBlank()) id = text(profile, "musicid", 100);
        return id;
    }
    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (!value.isString() && !value.isNumber()) return "";
        String text = value.asString("");
        return text.length() <= max ? text : "";
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐返回异常，请稍后重试");
    }

    /** Safe channel declaration for the connection UI. */
    public record Capabilities(boolean configured, List<String> channels) { }
    /** Public binding state without account credentials. */
    public record ConnectionStatus(boolean configured, boolean connected, boolean needsReconnect, String nickname, Long connectedAt) { }
    /** Website task ID, never the upstream key or music token. */
    public record QrView(String id, String channel, String image, String state, long expiresAt, String message) { }
    private static final class QrTask {
        final String id = UUID.randomUUID().toString();
        final String channel;
        final long startedAt = System.currentTimeMillis();
        final long expiresAt = startedAt + QR_TTL_MS;
        volatile String upstreamKey, token, image, message;
        volatile String state = "waiting";
        volatile boolean cancelled;
        long lastPoll;
        QrTask(String channel) { this.channel = channel; }
        QrView view() { return new QrView(id, channel, image, state, expiresAt, message); }
    }
}
