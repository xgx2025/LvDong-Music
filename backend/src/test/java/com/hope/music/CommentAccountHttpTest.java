package com.hope.music;

import com.hope.music.account.service.VerificationMailSender;
import com.hope.music.auth.util.PasswordUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.hope.music.recognition.service.RecognitionProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP/SQL regression with isolated database and explicitly fake mail delivery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:comment_account;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.sql.init.mode=never"})
@Import(CommentAccountHttpTest.MailConfiguration.class)
class CommentAccountHttpTest {
    static final java.nio.file.Path FILES;
    static {
        try { FILES=java.nio.file.Files.createTempDirectory("lvdong-migration-").toRealPath(); }
        catch(java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root",()->FILES.resolve("new").toString());
        registry.add("app.storage.legacy-root",()->FILES.resolve("legacy").toString());
        registry.add("app.storage.hls-root",()->FILES.resolve("hls").toString());
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeMail mail;
    final HttpClient client = HttpClient.newHttpClient();
    final JsonMapper mapper = JsonMapper.builder().build();
    static class FakeMail implements VerificationMailSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        boolean fail;
        public void sendCode(String email, String code) {
            if (fail) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            codes.put(email, code);
        }
    }
    @TestConfiguration
    static class MailConfiguration {
        @Bean @Primary FakeMail fakeMail() { return new FakeMail(); }
        @Bean @Primary RecognitionProvider recognitionFixture() { return text -> new RecognitionProvider.SongHint("public-song", "tester"); }
    }
    @BeforeEach void prepare() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
        jdbc.execute("DROP ALL OBJECTS");
        for (String file : List.of("003_auth_sessions.sql", "004_email_verifications.sql", "005_musician_materials.sql")) {
            String ddl = new String(getClass().getResourceAsStream("/db/" + file).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String sql : ddl.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        }
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(25) UNIQUE,email VARCHAR(25) UNIQUE,password VARCHAR(255),gender VARCHAR(2),state INT,bio TEXT,avatar VARCHAR(255),account_status INT DEFAULT 0,created_at TIMESTAMP)");
        jdbc.update("INSERT INTO users(id,username,email,password) VALUES(9007199254740993,'tester','tester@example.test',?),(2,'second','second@example.test',?)", PasswordUtil.hash("password-123"), PasswordUtil.hash("password-123"));
        jdbc.execute("CREATE TABLE songs(id BIGINT PRIMARY KEY,state INT,name VARCHAR(30),duration VARCHAR(5),album_id BIGINT,album_name VARCHAR(30),audio_url VARCHAR(255),cover_url VARCHAR(255),lyric_url VARCHAR(255),favorite_count INT DEFAULT 0)");
        jdbc.execute("INSERT INTO songs(id,state,name) VALUES(1,1,'public-song'),(2,0,'pending-song'),(3,1,'other-song')");
        jdbc.execute("CREATE TABLE singers(id BIGINT PRIMARY KEY,name VARCHAR(100),avatar VARCHAR(255),intro TEXT,gender VARCHAR(2),nationality VARCHAR(10),status INT)");
        jdbc.execute("CREATE TABLE song_singer(id BIGINT AUTO_INCREMENT PRIMARY KEY,song_id BIGINT,singer_id BIGINT,singer_name VARCHAR(30))");
        jdbc.execute("INSERT INTO song_singer(song_id,singer_id,singer_name) VALUES(1,9007199254740993,'tester')");
        jdbc.execute("CREATE TABLE comments(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,resource_type INT,resource_id BIGINT,content TEXT,parent_id BIGINT DEFAULT 0,reply_to_user_id BIGINT DEFAULT 0,like_count INT DEFAULT 0,reply_count INT DEFAULT 0,status INT DEFAULT 1,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE comment_likes(id BIGINT AUTO_INCREMENT PRIMARY KEY,comment_id BIGINT,user_id BIGINT,created_at TIMESTAMP,UNIQUE(comment_id,user_id))");
        mail.codes.clear(); mail.fail = false;
        java.nio.file.Files.createDirectories(FILES.resolve("legacy"));
    }
    HttpResponse<String> post(String path, Map<String, ?> body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode ok(String path, Map<String, ?> body, String token) throws Exception {
        var response = post(path, body, token);
        assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    String login(String email) throws Exception {
        return ok("session/login", Map.of("email", email, "password", "password-123"), null).path("tokens").path("accessToken").asString();
    }
    Map<String, Object> resource(String song) { return new HashMap<>(Map.of("resourceType", 1, "resourceId", song)); }
    String create(String song, String parent, String target, String token) throws Exception {
        var body = resource(song); body.putAll(Map.of("content", "<script>literal text</script>", "parentId", parent, "replyToUserId", target, "userId", "999"));
        return ok("comments", body, token).path("comment").path("id").asString();
    }
    @Test void commentPagingRepliesAndIdentityArePreserved() throws Exception {
        String token = login("tester@example.test"), second = login("second@example.test");
        String root = create("1", "0", "0", token);
        create("1", root, "9007199254740993", second);
        assertThat(jdbc.queryForObject("SELECT user_id FROM comments WHERE id=?", String.class, root)).isEqualTo("9007199254740993");
        var response = post("comments/page", resource("1"), token);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var page = mapper.readTree(response.body()).path("comments");
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("list").get(0).path("replyCount").asInt()).isEqualTo(1);
        assertThat(page.path("list").get(0).path("content").asString()).contains("<script>");
        var query = resource("1"); query.putAll(Map.of("parentId", root, "replyPage", 1, "replySize", 1));
        var replies = ok("comments/page", query, token).path("comments");
        assertThat(replies.path("list").get(0).path("replyToUsername").asString()).isEqualTo("tester");
        assertThat(ok("comments/count", resource("1"), token).path("totalCount").asString()).isEqualTo("2");
        query.put("replyPage", 2);
        assertThat(ok("comments/page", query, token).path("comments").path("list").size()).isZero();
    }
    @Test void hiddenSongsDeletedThreadsAndInvalidRelationsFailClosed() throws Exception {
        String token = login("tester@example.test");
        assertThat(post("comments/page", resource("1"), null).statusCode()).isEqualTo(401);
        assertThat(post("comments/page", resource("2"), token).statusCode()).isEqualTo(404);
        var wrong = resource("1"); wrong.put("resourceType", 2);
        assertThat(post("comments/count", wrong, token).statusCode()).isEqualTo(400);
        String root = create("1", "0", "0", token);
        var body = resource("3"); body.putAll(Map.of("parentId", root, "content", "cross-resource"));
        assertThat(post("comments", body, token).statusCode()).isEqualTo(400);
        body.put("resourceId", "1"); body.put("replyToUserId", "2");
        assertThat(post("comments", body, token).statusCode()).isEqualTo(400);
        String child = create("1", root, "0", token);
        body.put("parentId", child); body.put("replyToUserId", "0");
        assertThat(post("comments", body, token).statusCode()).isEqualTo(400);
        jdbc.update("UPDATE comments SET status=0 WHERE id=?", root);
        assertThat(post("comments/like", Map.of("commentId", child, "like", true), token).statusCode()).isEqualTo(400);
        assertThat(ok("comments/page", resource("1"), token).path("comments").path("total").asInt()).isZero();
        jdbc.update("UPDATE songs SET state=0 WHERE id=1");
        assertThat(post("comments/count", resource("1"), token).statusCode()).isEqualTo(404);
        assertThat(post("comments/like", Map.of("commentId", child, "like", true), token).statusCode()).isEqualTo(404);
    }
    @Test void concurrentLikesAreIdempotentAndPrivate() throws Exception {
        String token = login("tester@example.test"), second = login("second@example.test");
        String id = create("1", "0", "0", token);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Future<Integer>>();
            for (int i=0; i<12; i++) tasks.add(pool.submit(() -> post("comments/like", Map.of("commentId", id, "like", true), token).statusCode()));
            for (var task : tasks) assertThat(task.get()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_likes", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT like_count FROM comments WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(ok("comments/page", resource("1"), second).path("comments").path("list").get(0).path("userHasLiked").asBoolean()).isFalse();
        for (int i=0; i<3; i++) assertThat(ok("comments/like", Map.of("commentId", id, "like", false), token).path("likeCount").asInt()).isZero();
    }
    @Test void failedCountersRollbackReactionAndReplyInserts() throws Exception {
        String token = login("tester@example.test"), id = create("1", "0", "0", token);
        jdbc.execute("ALTER TABLE comments ADD CONSTRAINT counter_failure CHECK(like_count=0 AND reply_count=0)");
        assertThat(post("comments/like", Map.of("commentId", id, "like", true), token).statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_likes", Long.class)).isZero();
        var body = resource("1"); body.putAll(Map.of("parentId", id, "content", "rollback"));
        assertThat(post("comments", body, token).statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Long.class)).isEqualTo(1);
    }
    String send(String email) throws Exception {
        var response = ok("account/verification-code", Map.of("email", email), null);
        String code = mail.codes.get(email.toLowerCase(Locale.ROOT));
        assertThat(response.toString()).doesNotContain(code);
        return code;
    }
    Map<String, Object> registration(String email, String code) {
        return new HashMap<>(Map.of("username", "new-user", "email", email, "password", "new-password", "verificationCode", code));
    }
    @Test void registrationUsesSingleUseDurableCodeAndHashedPassword() throws Exception {
        String code = send("new@example.test");
        assertThat(jdbc.queryForObject("SELECT code_hash FROM email_verifications", String.class)).doesNotContain(code);
        var body = registration("new@example.test", code);
        assertThat(ok("account/registration", body, null).path("success").asBoolean()).isTrue();
        assertThat(ok("account/registration", body, null).path("success").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email='new@example.test'", Long.class)).isEqualTo(1);
        assertThat(PasswordUtil.verify("new-password", jdbc.queryForObject("SELECT password FROM users WHERE email='new@example.test'", String.class))).isTrue();
        assertThat(ok("session/login", Map.of("email", "new@example.test", "password", "new-password"), null).path("user").path("id").asString()).matches("[0-9]+");
    }
    @Test void codeAttemptsExpiryCooldownAndSmtpFailureAreEnforced() throws Exception {
        String code = send("new@example.test");
        assertThat(post("account/verification-code", Map.of("email", "NEW@example.test"), null).statusCode()).isEqualTo(429);
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i=0; i<5; i++) assertThat(ok("account/registration", registration("new@example.test", wrong), null).path("success").asBoolean()).isFalse();
        assertThat(ok("account/registration", registration("new@example.test", code), null).path("success").asBoolean()).isFalse();
        String expired = send("old@example.test");
        jdbc.update("UPDATE email_verifications SET expires_at=0 WHERE email='old@example.test'");
        assertThat(ok("account/verification", Map.of("email", "old@example.test", "code", expired), null).path("success").asBoolean()).isFalse();
        mail.fail = true;
        assertThat(post("account/verification-code", Map.of("email", "fail@example.test"), null).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email='fail@example.test' AND consumed=0", Long.class)).isZero();
        mail.fail = false; send("fail@example.test");
    }
    @Test void passwordResetRevokesAllAccessAndRefreshTokens() throws Exception {
        var login = ok("session/login", Map.of("email", "tester@example.test", "password", "password-123"), null);
        String access = login.path("tokens").path("accessToken").asString();
        String refresh = login.path("tokens").path("refreshToken").asString();
        String code = send("tester@example.test");
        ok("account/password-reset", Map.of("email", "tester@example.test", "newPassword", "replacement-123", "verificationCode", code), null);
        assertThat(post("session/profile", Map.of(), access).statusCode()).isEqualTo(401);
        assertThat(post("session/refresh", Map.of("refreshToken", refresh), null).statusCode()).isEqualTo(401);
        assertThat(post("session/login", Map.of("email", "tester@example.test", "password", "password-123"), null).statusCode()).isEqualTo(401);
        ok("session/login", Map.of("email", "tester@example.test", "password", "replacement-123"), null);
    }
    @Test void accountFailureRollsBackCodeAndRejectsInvalidInputs() throws Exception {
        String code = send("new@example.test");
        var body = registration("new@example.test", code); body.put("username", "tester");
        assertThat(post("account/registration", body, null).statusCode()).isEqualTo(409);
        body.put("username", "available");
        assertThat(ok("account/registration", body, null).path("success").asBoolean()).isTrue();
        assertThat(post("account/verification-code", Map.of("email", "bad\r\nBcc:evil"), null).statusCode()).isEqualTo(400);
        body.put("password", "密".repeat(25));
        assertThat(post("account/registration", body, null).statusCode()).isEqualTo(400);
    }
    String image() {
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aE4sAAAAASUVORK5CYII=";
    }
    long countFiles() throws Exception {
        try(var paths=java.nio.file.Files.walk(FILES)) { return paths.filter(java.nio.file.Files::isRegularFile).count(); }
    }
    HttpResponse<byte[]> get(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void profileWritesOnlyOwnAvatarAndRollbackKeepsExistingFiles() throws Exception {
        String token=login("tester@example.test");
        var body=new HashMap<String,Object>(Map.of("userId","2","username","new-name","gender","男","bio","updated","avatarBase64",image()));
        var saved=ok("creator/profile",body,token);String url=saved.path("avatarUrl").asString();
        assertThat(get(url,null).statusCode()).isEqualTo(200);
        assertThat(ok("session/profile",Map.of(),token).path("user").path("avatar").asString()).isEqualTo(url);
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=2",String.class)).isEqualTo("second");
        long before=countFiles();body.put("username","second");
        assertThat(post("creator/profile",body,token).statusCode()).isEqualTo(409);
        assertThat(countFiles()).isEqualTo(before);
        assertThat(get(url,null).statusCode()).isEqualTo(200);
        body.put("username","valid");body.put("avatarBase64","data:image/png;base64,PHNjcmlwdD4=");
        assertThat(post("creator/profile",body,token).statusCode()).isEqualTo(400);
        assertThat(countFiles()).isEqualTo(before);
    }
    @Test void musicianApplicationIsPrivateAndDuplicateCannotOverwriteMaterials() throws Exception {
        String token=login("tester@example.test");
        var body=Map.of("singerId","2","singerName","tester","gender","未知","nationality","中国","singerBio","bio","singerAvatarBase64",image(),"idCardFrontBase64",image(),"idCardBackBase64",image());
        assertThat(ok("creator/status",Map.of("singerId","2"),token).path("status").asString()).isEqualTo("normal");
        ok("creator/application",body,token);
        assertThat(ok("creator/status",Map.of("singerId","2"),token).path("status").asString()).isEqualTo("pending");
        String path=jdbc.queryForObject("SELECT front_path FROM musician_materials WHERE user_id=9007199254740993",String.class);
        byte[] material=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));long before=countFiles();
        assertThat(post("creator/application",body,token).statusCode()).isEqualTo(409);
        assertThat(countFiles()).isEqualTo(before);
        assertThat(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path))).isEqualTo(material);
        assertThat(get("/api/v1/assets/private/materials/"+java.nio.file.Path.of(path).getFileName(),token).statusCode()).isEqualTo(400);
        assertThat(post("creator/upload",Map.of(),token).statusCode()).isIn(400,403);
    }
    byte[] wav() throws Exception {
        var out=new java.io.ByteArrayOutputStream();
        byte[] samples=new byte[24000*2];
        var header=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36+samples.length).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples.length);
        out.write(header.array());out.write(samples);return out.toByteArray();
    }
    Map<String,Object> uploadBody() throws Exception {
        return new HashMap<>(Map.of("songName","uploaded","singerIdStr","9007199254740993","duration","00:03","album","single",
                "musicFile",Map.of("name","../../overwrite.wav","type","audio/wav","data","data:audio/wav;base64,"+Base64.getEncoder().encodeToString(wav())),
                "coverImage",Map.of("name","evil.png","type","image/png","data",image()),
                "lyricFile",Map.of("name","lyric.lrc","type","text/plain","data","data:text/plain;base64,"+Base64.getEncoder().encodeToString("[00:01]歌词".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
    }
    void approve() { jdbc.execute("INSERT INTO singers(id,name,status) VALUES(9007199254740993,'tester',1)"); }
    @Test void uploadIsTransactionalAndRequiresApprovedOwner() throws Exception {
        String token=login("tester@example.test");var body=uploadBody();
        assertThat(post("creator/upload",body,token).statusCode()).isEqualTo(403);
        approve();long before=countFiles();
        jdbc.execute("ALTER TABLE song_singer ADD CONSTRAINT relationship_failure CHECK(song_id=1)");
        assertThat(post("creator/upload",body,token).statusCode()).isEqualTo(500);
        assertThat(countFiles()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM songs",Long.class)).isEqualTo(3);
        jdbc.execute("ALTER TABLE song_singer DROP CONSTRAINT relationship_failure");
        String id=ok("creator/upload",body,token).path("songId").asString();
        assertThat(jdbc.queryForObject("SELECT state FROM songs WHERE id=?",Integer.class,id)).isZero();
        String source=jdbc.queryForObject("SELECT audio_url FROM songs WHERE id=?",String.class,id);
        assertThat(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(source))).isEqualTo(wav());
        assertThat(source).doesNotContain("overwrite");
        assertThat(post("library/song",Map.of("songId",id),token).statusCode()).isEqualTo(404);
    }
    @Test void recognitionUsesPublishedLibraryAndValidatesInput() throws Exception {
        String token=login("tester@example.test");
        assertThat(ok("recognition",Map.of("text","lyrics"),token).path("data").path("songId").asString()).isEqualTo("1");
        jdbc.execute("UPDATE songs SET state=0 WHERE id=1");
        assertThat(ok("recognition",Map.of("text","lyrics"),token).has("data")).isFalse();
        assertThat(post("recognition",Map.of("text",""),token).statusCode()).isEqualTo(400);
        assertThat(post("recognition",Map.of("text","lyrics"),null).statusCode()).isEqualTo(401);
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="media.regression",matches="true")
    void ffmpegHlsDeliveryKeysRangesAndRevocation() throws Exception {
        String token=login("tester@example.test");
        var input=FILES.resolve("legacy").resolve(UUID.randomUUID()+".wav");java.nio.file.Files.write(input,wav());
        jdbc.update("UPDATE songs SET audio_url=? WHERE id=1",input.toString());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/media/hls?action=processMp3&songId=1")).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        var manifestUrl="/api/v1/media/hls?action=serveHls&songId=1&fileName=index.m3u8";
        for(int i=0;i<100;i++) {
            var status=get("/api/v1/media/hls?action=checkStatus&songId=1",token);
            var state=mapper.readTree(status.body()).path("state").asString();
            assertThat(state).isNotEqualTo("FAILED");
            if(state.equals("READY"))break;
            Thread.sleep(100);
        }
        var manifest=get(manifestUrl,token);assertThat(manifest.statusCode()).isEqualTo(200);
        String text=new String(manifest.body(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("#EXT-X-ENDLIST","METHOD=AES-128");
        var keyPattern=java.util.regex.Pattern.compile("URI=\"([^\"]+)\"").matcher(text);assertThat(keyPattern.find()).isTrue();
        String keyUrl="/api/v1/media/"+keyPattern.group(1);
        String segment="/api/v1/media/"+text.lines().filter(line->line.startsWith("hls?")).findFirst().orElseThrow();
        assertThat(get(keyUrl,null).statusCode()).isEqualTo(401);
        byte[] key=get(keyUrl,token).body();assertThat(key).hasSize(16);
        var segmentResponse=get(segment,token);assertThat(segmentResponse.statusCode()).isEqualTo(200);
        byte[] iv=new byte[16];
        var ivMatcher=java.util.regex.Pattern.compile("IV=0x([a-fA-F0-9]{32})").matcher(text);
        if(ivMatcher.find())iv=HexFormat.of().parseHex(ivMatcher.group(1));
        var cipher=javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.IvParameterSpec(iv));
        assertThat(cipher.doFinal(segmentResponse.body())[0]).isEqualTo((byte)0x47);
        var range=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+segment)).header("Authorization","Bearer "+token).header("Range","bytes=0-15").GET().build();
        var partial=client.send(range,HttpResponse.BodyHandlers.ofByteArray());assertThat(partial.statusCode()).isEqualTo(206);assertThat(partial.body()).hasSize(16);
        assertThat(new String(get(manifestUrl+"&token="+token,null).body(),java.nio.charset.StandardCharsets.UTF_8)).contains("&token="+token);
        jdbc.update("UPDATE songs SET state=0 WHERE id=1");assertThat(get(keyUrl,token).statusCode()).isEqualTo(404);
        jdbc.update("UPDATE songs SET state=1 WHERE id=1");
        ok("session/logout",Map.of(),token);
        assertThat(get(manifestUrl,token).statusCode()).isEqualTo(401);assertThat(get(keyUrl,token).statusCode()).isEqualTo(401);assertThat(get(segment,token).statusCode()).isEqualTo(401);
        assertThat(java.nio.file.Files.readAllBytes(input)).isEqualTo(wav());
    }
    @AfterAll static void removeOwnTemporaryFiles() throws Exception {
        try(var files=java.nio.file.Files.walk(FILES)) {
            for(var path:files.sorted(Comparator.reverseOrder()).toList()) {
                if(!path.toAbsolutePath().normalize().startsWith(FILES))throw new IllegalStateException("Unexpected fixture path");
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }
    @Test void originalWebSocketExampleBroadcastsToBothConnections() throws Exception {
        class Listener implements WebSocket.Listener {
            final BlockingQueue<String> messages=new LinkedBlockingQueue<>();
            final StringBuilder buffer=new StringBuilder();
            @Override public void onOpen(WebSocket socket) { socket.request(1); }
            @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {
                buffer.append(data);if(last) { messages.add(buffer.toString());buffer.setLength(0); }
                socket.request(1);return null;
            }
        }
        var first=new Listener();var second=new Listener();
        var a=client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/websocket"),first).get(5,TimeUnit.SECONDS);
        var b=client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/api/v1/websocket"),second).get(5,TimeUnit.SECONDS);
        try {
            assertThat(first.messages.poll(5,TimeUnit.SECONDS)).contains("连接已建立");
            assertThat(second.messages.poll(5,TimeUnit.SECONDS)).contains("连接已建立");
            a.sendText("fixture-message",true).get(5,TimeUnit.SECONDS);
            assertThat(first.messages.poll(5,TimeUnit.SECONDS)).contains("fixture-message");
            assertThat(second.messages.poll(5,TimeUnit.SECONDS)).contains("fixture-message");
        } finally { a.abort();b.abort(); }
    }
}
