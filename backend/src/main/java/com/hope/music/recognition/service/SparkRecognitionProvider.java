package com.hope.music.recognition.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Configured Spark HTTP integration; no embedded credentials or fabricated results. */
@Component
public class SparkRecognitionProvider implements RecognitionProvider {
    private final String endpoint, key, model;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public SparkRecognitionProvider(@Value("${app.recognition.endpoint:https://spark-api-open.xf-yun.com/v1/chat/completions}") String endpoint,
            @Value("${app.recognition.api-key:}") String key,@Value("${app.recognition.model:4.0Ultra}") String model) {
        this.endpoint=endpoint;this.key=key;this.model=model;
    }
    public SongHint recognize(String text) {
        if(key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"识曲服务尚未配置");
        try (var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var body=Map.of("model",model,"stream",false,"max_tokens",512,"temperature",0.1,"messages",List.of(
                    Map.of("role","system","content","根据歌词线索推测歌曲和歌手。只返回JSON对象，包含song和singer两个字符串字段。"),Map.of("role","user","content",text)));
            var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(12))
                    .header("Content-Type","application/json").header("Authorization","Bearer "+key)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200||response.body().length()>65536)throw new IllegalStateException();
            var content=mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asString();
            String json=content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            var hint=mapper.readTree(json);String song=hint.path("song").asString(),singer=hint.path("singer").asString();
            if(song.isBlank()||singer.isBlank()||song.length()>200||singer.length()>200)throw new IllegalStateException();
            return new SongHint(song,singer);
        } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw failed(); }
        catch(Exception ex) { throw failed(); }
    }
    private ResponseStatusException failed() { return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"识曲服务暂不可用，请稍后重试"); }
}
