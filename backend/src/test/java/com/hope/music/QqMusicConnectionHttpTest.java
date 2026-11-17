package com.hope.music;

import com.hope.music.auth.util.PasswordUtil;
import com.hope.music.provider.dao.QqMusicConnectionDao;
import com.hope.music.provider.service.MusicTokenCipher;
import com.hope.music.provider.service.QqMusicClient;
import com.hope.music.provider.service.QqMusicConnectionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

/** Real website HTTP plus a controlled QQ service verifies ownership and secret boundaries. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:qq_connection;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never" })
class QqMusicConnectionHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Set<String> REVOKED = ConcurrentHashMap.newKeySet();
    private static final List<String> HEADERS = Collections.synchronizedList(new ArrayList<>());
    private static final String IMAGE = "data:image/png;base64,iVBORw0KGgo=";
    private static volatile String mode = "ok";
    private static volatile CountDownLatch verifyEntered, verifyRelease;
    private static final HttpServer UPSTREAM = upstream();
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired QqMusicClient qq;
    @Autowired MusicTokenCipher cipher;
    @Autowired QqMusicConnectionDao dao;
    final HttpClient http = HttpClient.newHttpClient();
    String first, second, userId;

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        registry.add("app.qq-music.api-base", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
        registry.add("app.qq-music.connection-key", () -> KEY);
    }
    static HttpServer upstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String token = exchange.getRequestHeaders().getFirst("X-QQ-Session");
                if (token != null) HEADERS.add(token);
                int status = mode.equals("offline") ? 502 : 200;
                Object body = Map.of("code", 200);
                if (path.equals("/login/channels")) body = Map.of("code", 200, "data", Map.of("channels", List.of("qqmusic", "qq", "wechat"), "sessionMode", mode.equals("sealed") ? "sealed" : "stored"));
                if (path.equals("/login/qr/key")) body = Map.of("code", 200, "data", Map.of("unikey", "upstream-key-" + IDS.incrementAndGet()));
                if (path.equals("/login/qr/create")) body = Map.of("code", 200, "data", Map.of("qrimg", mode.equals("bad-image") ? "https://tracking.invalid/qr" : IMAGE));
                if (path.equals("/login/qr/check")) {
                    String key = exchange.getRequestURI().getRawQuery().replace("key=", "");
                    body = Map.of("code", mode.equals("waiting") ? 801 : mode.equals("scanned") ? 802 : mode.equals("expired") ? 800 : 803,
                            "cookie", "qqmusic_session=secret-" + key + "; Path=/", "musickey", "must-not-leak");
                }
                if (path.equals("/login/status")) {
                    if (mode.equals("hold-status")) {
                        verifyEntered.countDown();
                        try { verifyRelease.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                    boolean expired = mode.equals("expired-auth") || REVOKED.contains(token);
                    status = expired ? 401 : status;
                    body = Map.of("code", status, "data", Map.of("profile", Map.of("musicid", "qq-" + token, "nickname", "QQ Tester", "musickey", "must-not-leak")));
                }
                if (path.equals("/logout") && token != null) REVOKED.add(token);
                if (path.equals("/user/playlist")) {
                    boolean next = query(exchange.getRequestURI(), "favoriteOffset", 0) > 0;
                    body = Map.of("code", 200, "playlist", next ? List.of(Map.of("tid", "678", "name", "更多收藏", "songnum", 0)) : List.of(
                            Map.of("tid", "123", "dirId", 7, "dirName", "我的歌单", "songNum", 205),
                            Map.of("tid", "234", "dirId", 36, "name", "收藏歌单", "songnum", 2),
                            Map.of("tid", "345", "dirId", 8, "dirName", "空歌单", "songNum", 0)),
                            "more", mode.equals("list-more") && !next, "nextFavoriteOffset", 1000, "total", 4);
                }
                if (path.equals("/user/playlist-detail")) {
                    int offset = query(exchange.getRequestURI(), "offset", 0), limit = query(exchange.getRequestURI(), "limit", 100);
                    int total = mode.equals("too-large") ? 6000 : mode.equals("small") ? 2 : query(exchange.getRequestURI(), "tid", 0) == 345 ? 0 : 205;
                    int start = mode.equals("repeat") ? 0 : offset;
                    int count = Math.min(limit, Math.max(0, total - offset));
                    if (mode.equals("empty-more") && offset > 0) count = 0;
                    body = Map.of("code", 200, "songs", rawSongs(start, count), "total", total, "more", offset + count < total);
                    if (mode.equals("broken") && offset > 0) status = 502;
                }
                if (path.equals("/user/liked-songs")) body = Map.of("code", 200, "songs", rawSongs(0, 2), "total", 2, "more", false);
                if (path.equals("/getSongListDetail")) body = Map.of("response", Map.of("code", 0, "cdlist", List.of(
                        Map.of("dissname", mode.equals("private-shell") ? "" : "收藏歌单", "songlist", rawSongs(0, 2), "total_song_num", 2))));
                if (path.equals("/getMusicPlay")) {
                    String mid = queryText(exchange.getRequestURI(), "songmid");
                    boolean preview = queryText(exchange.getRequestURI(), "preview").equals("1");
                    boolean empty = mode.equals("no-audio") || (mode.equals("preview") && !preview);
                    String url = empty ? "" : mode.equals("unsafe-audio") ? "https://evil.invalid/song.mp3" : "https://isure.stream.qqmusic.qq.com/song.mp3?vkey=demo";
                    body = Map.of("data", Map.of("playUrl", Map.of(mid, Map.of("url", url, "preview", preview))));
                }
                byte[] bytes = JSON.writeValueAsBytes(body);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start(); return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @AfterAll static void stop() { UPSTREAM.stop(0); }
    static String queryText(URI uri, String name) {
        for (String part : Objects.toString(uri.getRawQuery(), "").split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        return "";
    }
    static int query(URI uri, String name, int fallback) {
        try { return Integer.parseInt(queryText(uri, name)); } catch (NumberFormatException e) { return fallback; }
    }
    static List<Map<String, Object>> rawSongs(int start, int count) {
        var songs = new ArrayList<Map<String, Object>>();
        for (int i = start; i < start + count; i++) songs.add(Map.of("mid", "mid" + i, "title", "QQ 歌曲 " + i,
                "interval", 181, "album", Map.of("mid", "album1", "name", "测试专辑"),
                "file", Map.of("media_mid", "media" + i), "singer", List.of(Map.of("mid", "singer1", "name", "测试歌手")), "musickey", "must-not-leak"));
        return songs;
    }
    @BeforeEach void prepare() throws Exception {
        mode = "ok"; REVOKED.clear(); HEADERS.clear();
        try (var connection = jdbc.getDataSource().getConnection()) { assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:"); }
        jdbc.execute("DROP ALL OBJECTS");
        for (String resource : List.of("003_auth_sessions.sql", "006_qq_music_connection.sql", "007_qq_playlist.sql")) {
            String ddl = new String(getClass().getResourceAsStream("/db/" + resource).readAllBytes(), StandardCharsets.UTF_8);
            for (String sql : ddl.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        }
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(50),password VARCHAR(100),email VARCHAR(255),avatar VARCHAR(255),gender VARCHAR(20),bio VARCHAR(255),created_at TIMESTAMP)");
        userId = Integer.toString(IDS.addAndGet(10));
        first = login(userId); second = login(Integer.toString(Integer.parseInt(userId) + 1));
    }
    String login(String id) throws Exception {
        jdbc.update("INSERT INTO users(id,username,password,email) VALUES(?,'tester',?,?)", id, PasswordUtil.hash("password-123"), id + "@example.test");
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/session/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + id + "@example.test\",\"password\":\"password-123\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).path("tokens").path("accessToken").asString();
    }
    HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/music-providers/qq" + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).doesNotContain("musickey", "secret-", "upstream-key-", "encryptedToken", "Set-Cookie");
        return JSON.readTree(response.body()).path("data");
    }
    String start(String token) throws Exception {
        JsonNode view = ok(call("POST", "/login", "{\"channel\":\"qqmusic\",\"userId\":\"forged\"}", token));
        assertThat(view.path("image").asString()).isEqualTo(IMAGE);
        return view.path("id").asString();
    }
    void connect(String token) throws Exception {
        String id = start(token); ok(call("POST", "/login/" + id + "/check", null, token));
    }
    @Test void importsEveryPageAndUpdatesSnapshotWithoutDuplicatingOrLeakingToOtherUser() throws Exception {
        connect(first);
        var lists = ok(call("GET", "/playlists", null, first));
        assertThat(lists.path("items").get(0).path("id").asString()).isEqualTo("liked");
        var imported = ok(call("POST", "/imports", "{\"playlistId\":\"123\",\"userId\":\"forged\"}", first));
        String id = imported.path("id").asString();
        assertThat(imported.path("songCount").asInt()).isEqualTo(205);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_playlist_song", Integer.class)).isEqualTo(205);
        assertThat(ok(call("GET", "/imports/" + id + "/songs?offset=200", null, first)).path("items")).hasSize(5);
        assertThat(ok(call("GET", "/imports/" + id + "/songs?offset=200", null, first)).path("more").asBoolean()).isFalse();
        var song = ok(call("GET", "/imports/" + id + "/songs?limit=1", null, first)).path("items").get(0);
        assertThat(song.path("provider").asString()).isEqualTo("qq");
        assertThat(song.path("providerSongId").asString()).isEqualTo("mid0");
        assertThat(song.path("mediaMid").asString()).isEqualTo("media0");
        assertThat(song.path("duration").asString()).isEqualTo("3:01");
        assertThat(song.path("singers").get(0).path("name").asString()).isEqualTo("测试歌手");
        assertThat(ok(call("GET", "/imports", null, second))).isEmpty();
        assertThat(call("GET", "/imports/" + id + "/songs", null, second).statusCode()).isEqualTo(404);
        ok(call("DELETE", "/imports/" + id, null, second));
        mode = "small";
        var updated = ok(call("POST", "/imports", "{\"playlistId\":\"123\"}", first));
        assertThat(updated.path("id").asString()).isEqualTo(id);
        assertThat(updated.path("songCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_playlist", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_playlist_song", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT metadata FROM qq_playlist_song WHERE position=0", String.class)).doesNotContain("musickey", "http://127", "token", "playUrl");
        ok(call("DELETE", "/imports/" + id, null, first));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_playlist_song", Integer.class)).isZero();
    }
    @Test void incompleteOrRepeatedPagesAndDatabaseFailureKeepPreviousSnapshot() throws Exception {
        connect(first); mode = "small";
        String id = ok(call("POST", "/imports", "{\"playlistId\":\"123\"}", first)).path("id").asString();
        for (String failure : List.of("broken", "repeat", "empty-more")) {
            mode = failure;
            assertThat(call("POST", "/imports", "{\"playlistId\":\"123\"}", first).statusCode()).isEqualTo(503);
            assertThat(ok(call("GET", "/imports/" + id + "/songs", null, first)).path("total").asInt()).isEqualTo(2);
        }
        mode = "too-large";
        assertThat(call("POST", "/imports", "{\"playlistId\":\"123\"}", first).statusCode()).isEqualTo(422);
        assertThat(ok(call("GET", "/imports/" + id + "/songs", null, first)).path("total").asInt()).isEqualTo(2);
        jdbc.execute("ALTER TABLE qq_playlist_song ADD CONSTRAINT fail_large_import CHECK(position < 2)");
        mode = "ok";
        assertThat(call("POST", "/imports", "{\"playlistId\":\"123\"}", first).statusCode()).isEqualTo(500);
        assertThat(ok(call("GET", "/imports/" + id + "/songs", null, first)).path("total").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_playlist_song", Integer.class)).isEqualTo(2);
    }
    @Test void loadsLikedPublicFavoriteEmptyPlaylistsAndContinuation() throws Exception {
        connect(first);
        assertThat(ok(call("GET", "/playlists/liked/songs", null, first)).path("items")).hasSize(2);
        assertThat(ok(call("GET", "/playlists/234/songs", null, first)).path("items")).hasSize(2);
        assertThat(ok(call("POST", "/imports", "{\"playlistId\":\"345\"}", first)).path("songCount").asInt()).isZero();
        mode = "private-shell";
        assertThat(call("GET", "/playlists/234/songs", null, first).statusCode()).isEqualTo(422);
        mode = "list-more";
        assertThat(ok(call("GET", "/playlists", null, first)).path("nextFavoriteOffset").asInt()).isEqualTo(1000);
        assertThat(ok(call("GET", "/playlists?favoriteOffset=1000", null, first)).path("items").get(0).path("id").asString()).isEqualTo("678");
    }
    @Test void checksBindingInputAndPlaylistMembershipAndKeepsImportsAfterDisconnect() throws Exception {
        assertThat(call("GET", "/playlists", null, first).statusCode()).isEqualTo(409);
        assertThat(call("GET", "/imports", null, null).statusCode()).isEqualTo(401);
        connect(first);
        assertThat(call("GET", "/playlists/999/songs", null, first).statusCode()).isEqualTo(404);
        assertThat(call("GET", "/playlists/123/songs?offset=-1", null, first).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/playlists/123/songs?limit=101", null, first).statusCode()).isEqualTo(400);
        assertThat(call("POST", "/imports", "{\"playlistId\":\"https://evil.invalid\"}", first).statusCode()).isEqualTo(400);
        ok(call("POST", "/imports", "{\"playlistId\":\"liked\"}", first));
        ok(call("DELETE", "/connection", null, first));
        assertThat(ok(call("GET", "/imports", null, first))).hasSize(1);
        assertThat(call("GET", "/playlists", null, first).statusCode()).isEqualTo(409);
    }
    @Test void resolvesCurrentUsersAudioWithoutWebsiteTokenAndMarksPreview() throws Exception {
        connect(first);
        String body = "{\"songMid\":\"mid0\",\"mediaMid\":\"media0\"}";
        var audio = ok(call("POST", "/playback", body, first));
        assertThat(audio.path("url").asString()).startsWith("https://isure.stream.qqmusic.qq.com/");
        assertThat(audio.path("preview").asBoolean()).isFalse();
        mode = "preview";
        assertThat(ok(call("POST", "/playback", body, first)).path("preview").asBoolean()).isTrue();
        mode = "no-audio";
        assertThat(call("POST", "/playback", body, first).statusCode()).isEqualTo(422);
        mode = "unsafe-audio";
        assertThat(call("POST", "/playback", body, first).statusCode()).isEqualTo(503);
        assertThat(HEADERS).allMatch(value -> value.startsWith("secret-"));
    }
    @Test void bindsOnlyOwnerEncryptsAndSurvivesServiceRecreation() throws Exception {
        String task = start(first);
        assertThat(call("POST", "/login/" + task + "/check", null, second).statusCode()).isEqualTo(404);
        ok(call("DELETE", "/login/" + task, null, second));
        assertThat(ok(call("POST", "/login/" + task + "/check", null, first)).path("state").asString()).isEqualTo("confirmed");
        assertThat(ok(call("GET", "/connection", null, first)).path("connected").asBoolean()).isTrue();
        assertThat(ok(call("GET", "/connection", null, second)).path("connected").asBoolean()).isFalse();
        String encrypted = jdbc.queryForObject("SELECT encrypted_token FROM qq_music_connection WHERE user_id=?", String.class, userId);
        assertThat(encrypted).startsWith("v1.").doesNotContain("secret-", "upstream-key-");
        assertThatThrownBy(() -> cipher.decrypt("other-user", encrypted)).isInstanceOf(IllegalStateException.class);
        var recreated = new QqMusicConnectionService(qq, new MusicTokenCipher(KEY), new QqMusicConnectionDao(jdbc));
        assertThat(recreated.status(userId).connected()).isTrue();
        assertThat(HEADERS).allMatch(value -> value.startsWith("secret-"));
        ok(call("DELETE", "/connection", null, second));
        assertThat(recreated.status(userId).connected()).isTrue();
        ok(call("DELETE", "/connection", null, first));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_music_connection", Integer.class)).isZero();
        assertThat(REVOKED).hasSize(1);
        assertThat(call("POST", "/login/" + task + "/check", null, first).statusCode()).isEqualTo(404);
    }
    @Test void cancelPreventsLateConfirmationAndAuthenticationIsRequired() throws Exception {
        assertThat(call("GET", "/connection", null, null).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/login", "{\"channel\":\"qqmusic\"}", null).statusCode()).isEqualTo(401);
        String task = start(first);
        ok(call("DELETE", "/login/" + task, null, first));
        assertThat(call("POST", "/login/" + task + "/check", null, first).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_music_connection", Integer.class)).isZero();
    }
    @Test void cancellationWhileAccountVerificationIsInFlightPreventsBinding() throws Exception {
        String task = start(first);
        verifyEntered = new CountDownLatch(1); verifyRelease = new CountDownLatch(1); mode = "hold-status";
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var checking = pool.submit(() -> call("POST", "/login/" + task + "/check", null, first));
            assertThat(verifyEntered.await(3, TimeUnit.SECONDS)).isTrue();
            try { ok(call("DELETE", "/login/" + task, null, first)); }
            finally { verifyRelease.countDown(); }
            assertThat(ok(checking.get()).path("state").asString()).isEqualTo("expired");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_music_connection", Integer.class)).isZero();
        }
    }
    @Test void concurrentUsersKeepSeparateMusicSessions() throws Exception {
        String a = start(first), b = start(second);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstCheck = pool.submit(() -> call("POST", "/login/" + a + "/check", null, first));
            var secondCheck = pool.submit(() -> call("POST", "/login/" + b + "/check", null, second));
            ok(firstCheck.get()); ok(secondCheck.get());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT account_id) FROM qq_music_connection", Integer.class)).isEqualTo(2);
        ok(call("DELETE", "/connection", null, first));
        assertThat(ok(call("GET", "/connection", null, second)).path("connected").asBoolean()).isTrue();
    }
    @Test void reportsQrStatesRejectsUnsupportedModeAndSanitizesFailures() throws Exception {
        var caps = ok(call("GET", "/capabilities", null, first));
        assertThat(caps.path("channels").toString()).isEqualTo("[\"qqmusic\"]");
        assertThat(call("POST", "/login", "{\"channel\":\"bogus\"}", first).statusCode()).isEqualTo(400);
        assertThat(call("POST", "/login", "{\"channel\":\"wechat\"}", first).statusCode()).isEqualTo(400);
        String task = start(first); mode = "scanned";
        assertThat(ok(call("POST", "/login/" + task + "/check", null, first)).path("state").asString()).isEqualTo("scanned");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_music_connection", Integer.class)).isZero();
        mode = "offline";
        var response = call("GET", "/capabilities", null, first);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain("127.0.0.1", "secret-", "musickey");
        mode = "sealed";
        assertThat(call("GET", "/capabilities", null, first).statusCode()).isEqualTo(503);
    }
    @Test void expiredAuthRequiresReconnectButOutageKeepsBinding() throws Exception {
        String task = start(first); ok(call("POST", "/login/" + task + "/check", null, first));
        mode = "offline";
        assertThat(call("GET", "/connection", null, first).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_music_connection", Integer.class)).isEqualTo(1);
        mode = "expired-auth";
        assertThat(ok(call("GET", "/connection", null, first)).path("needsReconnect").asBoolean()).isTrue();
        mode = "ok";
        assertThat(ok(call("GET", "/connection", null, first)).path("connected").asBoolean()).isTrue();
    }
    @Test void rejectsExternalQrImageAndOptionalConfigNeedsNoTable() throws Exception {
        mode = "bad-image";
        assertThat(call("POST", "/login", "{\"channel\":\"qqmusic\"}", first).statusCode()).isEqualTo(503);
        var disabled = new QqMusicConnectionService(new QqMusicClient(""), new MusicTokenCipher(""), dao);
        jdbc.execute("DROP TABLE qq_music_connection");
        assertThat(disabled.status(userId).configured()).isFalse();
        assertThat(disabled.capabilities().channels()).isEmpty();
    }
}
