package com.hope.music.provider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Fixed upstream adapter; never forwards browser credentials, cookies or upstream errors. */
@Component
public class QqMusicClient {
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

    public QqMusicClient(@Value("${app.qq-music.api-base:}") String base) {
        this.base = base.strip().replaceAll("/+$", "");
        if (!this.base.isEmpty()) {
            URI uri = URI.create(this.base);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("QQ_MUSIC_API_BASE must be an HTTP service URL");
            }
        }
    }

    public boolean configured() { return !base.isEmpty(); }

    public JsonNode get(String path, Map<String, String> params, String token) {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐连接服务尚未配置");
        String query = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(base + path + (query.isEmpty() ? "" : "?" + query)))
                .timeout(Duration.ofSeconds(path.equals("/getMusicPlay") ? 90 : 30)).header("Accept", "application/json");
        if (token != null) request.header("X-QQ-Session", token);
        try {
            var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() == 401) throw new ResponseStatusException(HttpStatus.CONFLICT, "QQ 音乐连接已失效，请重新连接");
                if (response.statusCode() == 429) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "QQ 音乐操作过于频繁，请稍后再试");
                if (response.statusCode() != 200) throw unavailable();
                byte[] body = input.readNBytes(8_388_609);
                if (body.length > 8_388_608) throw unavailable();
                JsonNode json = mapper.readTree(body);
                if (json == null || !json.isObject()) throw unavailable();
                if (path.equals("/getMusicPlay")) {
                    if (!json.path("data").path("playUrl").isObject()) throw unavailable();
                } else if (path.equals("/getSongListDetail")) {
                    if (!json.path("response").isObject()) throw unavailable();
                } else {
                    if (!json.path("code").isNumber()) throw unavailable();
                    if (!path.equals("/login/qr/check") && json.path("code").asInt() != 200) throw unavailable();
                }
                return json;
            }
        } catch (ResponseStatusException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception e) { throw unavailable(); }
    }

    public void cancelQuietly(String key) { try { get("/login/qr/cancel", Map.of("key", key), null); } catch (RuntimeException ignored) { } }
    public void logoutQuietly(String token) { try { get("/logout", Map.of(), token); } catch (RuntimeException ignored) { } }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "暂时无法连接 QQ 音乐，请稍后重试");
    }
}
