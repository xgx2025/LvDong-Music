package com.hope.music;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Real embedded HTTP server and explicitly pinned in-memory SQL; never uses developer DB settings. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:boot_search;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"})
class SearchHttpTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void prepare() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertIsolatedDatabase(connection.getMetaData().getURL());
        }
        jdbc.execute("DROP TABLE IF EXISTS song_singer");
        jdbc.execute("DROP TABLE IF EXISTS songs");
        jdbc.execute("DROP TABLE IF EXISTS singers");
        jdbc.execute("CREATE TABLE songs(id BIGINT PRIMARY KEY,name VARCHAR(50),duration VARCHAR(10),album_id BIGINT,"
                + "album_name VARCHAR(50),cover_url VARCHAR(255),lyric_url VARCHAR(255),audio_url VARCHAR(255),favorite_count INT DEFAULT 0,state INT)");
        jdbc.execute("CREATE TABLE song_singer(song_id BIGINT,singer_id BIGINT,singer_name VARCHAR(50),PRIMARY KEY(song_id,singer_id))");
        jdbc.execute("CREATE TABLE singers(id BIGINT PRIMARY KEY,name VARCHAR(50),avatar VARCHAR(255))");
        jdbc.execute("INSERT INTO songs(id,name,state,audio_url) VALUES(9007199254740993,'match',1,'private.mp3'),"
                + "(9007199254740994,'match',1,'second.mp3'),(3,'match',0,'pending.mp3'),(4,'match',2,'rejected.mp3')");
        jdbc.execute("INSERT INTO song_singer VALUES(9007199254740993,11,'artist'),(9007199254740993,12,'artist'),"
                + "(9007199254740994,11,'artist'),(3,11,'artist'),(4,11,'artist')");
        jdbc.execute("INSERT INTO singers VALUES(11,'artist',NULL),(12,'artist',NULL)");
    }

    protected void assertIsolatedDatabase(String url) {
        assertThat(url).startsWith("jdbc:h2:mem:");
    }

    @Test void publicationAndStableSongPagination() throws Exception {
        JsonNode first = json(get("/api/v1/search?keyword=artist&pageSize=1"));
        assertThat(first.path("code").asInt()).isZero();
        JsonNode data = first.path("data");
        assertThat(data.path("songCount").asInt()).isEqualTo(2);
        assertThat(data.path("singerCount").asInt()).isEqualTo(2);
        assertThat(data.path("songs").get(0).path("songId").asString()).isEqualTo("9007199254740993");
        assertThat(data.path("songs").get(0).path("singers").size()).isEqualTo(2);
        assertThat(data.path("songs").get(0).has("audioUrl")).isFalse();
        assertThat(data.path("albums").size()).isZero();
        assertThat(data.path("playlists").size()).isZero();
        assertThat(json(get("/api/v1/search?keyword=artist&page=2&pageSize=1")).path("data").path("songs")
                .get(0).path("songId").asString()).isEqualTo("9007199254740994");
    }

    @Test void legacyAdapterAndInvalidTokenRemainAnonymous() throws Exception {
        var request = HttpRequest.newBuilder(uri("/searchServlet?action=search"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer invalid")
                .POST(HttpRequest.BodyPublishers.ofString("{\"keyword\":\"match\",\"page\":-1,\"pageSize\":500,\"accessToken\":\"invalid\"}")).build();
        JsonNode legacy = json(client.send(request, HttpResponse.BodyHandlers.ofString()));
        assertThat(legacy.path("success").asBoolean()).isTrue();
        assertThat(legacy.path("message").path("songs").size()).isEqualTo(2);
        assertThat(legacy.path("message").path("songs").get(0).path("userHasFavorite").asBoolean()).isFalse();
    }

    @Test void invalidAndOversizedParametersReturnStructured400() throws Exception {
        for (String query : new String[]{"page=0", "pageSize=101", "page=abc", "page=2147483648", "keyword=" + "x".repeat(201)}) {
            var response = get("/api/v1/search?" + query);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(mapper.readTree(response.body()).path("code").asInt()).isEqualTo(400);
        }
    }

    @Test void malformedJsonIsRejected() throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri("/searchServlet?action=search"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test void emptyAndHugePageAreSafe() throws Exception {
        assertThat(json(get("/api/v1/search?keyword=missing")).path("data").path("songs").size()).isZero();
        assertThat(json(get("/api/v1/search?page=2147483647&pageSize=100")).path("data").path("songs").size()).isZero();
        assertThat(json(get("/api/v1/search?keyword=%27%20OR%201%3D1%20--")).path("data").path("songCount").asInt()).isZero();
    }

    @Test void databaseFailureCannotMasqueradeAsEmptySuccess() throws Exception {
        jdbc.execute("DROP TABLE song_singer");
        var response = get("/api/v1/search?keyword=match");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).doesNotContain("SELECT", "jdbc:", "song_singer");
        var legacy = client.send(HttpRequest.newBuilder(uri("/searchServlet?action=search"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(json(legacy).path("success").asBoolean()).isFalse();
        // Leave a valid fixture for the runner's subsequent packaged-JAR smoke test.
        prepare();
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode json(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
}
