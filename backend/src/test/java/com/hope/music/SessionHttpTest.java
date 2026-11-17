package com.hope.music;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.hope.music.auth.service.SessionService;
import com.hope.music.auth.dao.SessionDao;
import com.hope.music.auth.util.PasswordUtil;
import java.net.*;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP authentication and isolated persistent SQL, including simulated service recreation. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:boot_auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=never"})
class SessionHttpTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 final HttpClient client=HttpClient.newHttpClient();
 final JsonMapper mapper=JsonMapper.builder().build();
 @BeforeEach void prepare() throws Exception {
  try(var connection=jdbc.getDataSource().getConnection()) { assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:"); }
  jdbc.execute("DROP ALL OBJECTS");
  String ddl=new String(getClass().getResourceAsStream("/db/003_auth_sessions.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  for(String sql:ddl.split(";")) if(!sql.isBlank()) jdbc.execute(sql);
  jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(50),password VARCHAR(100),email VARCHAR(255),avatar VARCHAR(255),gender VARCHAR(20),bio VARCHAR(255),created_at TIMESTAMP)");
  jdbc.update("INSERT INTO users(id,username,password,email) VALUES(9007199254740993,'tester',?,'tester@example.test')",PasswordUtil.hash("password-123"));
  jdbc.execute("CREATE TABLE songs(id BIGINT PRIMARY KEY,name VARCHAR(50),duration VARCHAR(10),album_id BIGINT,album_name VARCHAR(50),cover_url VARCHAR(255),lyric_url VARCHAR(255),favorite_count INT DEFAULT 0,state INT)");
  jdbc.execute("CREATE TABLE song_singer(song_id BIGINT,singer_id BIGINT,singer_name VARCHAR(50))");
  jdbc.execute("CREATE TABLE song_likes(song_id BIGINT,user_id BIGINT,created_at TIMESTAMP,UNIQUE(song_id,user_id))");
  jdbc.execute("INSERT INTO songs(id,name,state) VALUES(1,'public',1),(2,'pending',0)");
 }
 HttpResponse<String> post(String path,String body,String token) throws Exception {
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/"+path)).header("Content-Type","application/json");
  if(token!=null) request.header("Authorization","Bearer "+token);
  return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 JsonNode json(HttpResponse<String> r) throws Exception {assertThat(r.statusCode()).isEqualTo(200);return mapper.readTree(r.body());}
 JsonNode login() throws Exception {return json(post("session/login","{\"email\":\"tester@example.test\",\"password\":\"password-123\"}",null));}
 @Test void identityRefreshLogoutAndRecreatedValidator() throws Exception {
  var login=login();String a=login.path("tokens").path("accessToken").asString(),r=login.path("tokens").path("refreshToken").asString();
  assertThat(login.path("user").path("id").asString()).isEqualTo("9007199254740993");
  assertThat(login.toString()).doesNotContain("password","jdbc:");
  assertThat(jdbc.queryForObject("SELECT refresh_hash FROM auth_sessions",String.class)).doesNotContain(r);
  assertThat(json(post("session/profile","{\"userId\":\"2\"}",a)).path("user").path("id").asString()).isEqualTo("9007199254740993");
  assertThat(post("session/profile","{}",r).statusCode()).isEqualTo(401);
  String renewal="{\"refreshToken\":\""+r+"\"}";
  String a2=json(post("session/refresh",renewal,null)).path("tokens").path("accessToken").asString();
  json(post("session/logout",renewal,a));
  assertThat(post("session/profile","{}",a).statusCode()).isEqualTo(401);
  assertThat(post("session/profile","{}",a2).statusCode()).isEqualTo(401);
  assertThat(post("session/refresh",renewal,null).statusCode()).isEqualTo(401);
  var recreated=new SessionService(new SessionDao(jdbc));
  assertThatThrownBy(()->recreated.identity("Bearer "+a)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void favoritesAreIdempotentPrivateAndPublicationFiltered() throws Exception {
  String a=login().path("tokens").path("accessToken").asString();
  for(int i=0;i<3;i++) json(post("library/favorite","{\"songId\":\"1\",\"userId\":\"123\"}",a));
  assertThat(jdbc.queryForObject("SELECT favorite_count FROM songs WHERE id=1",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT user_id FROM song_likes",String.class)).isEqualTo("9007199254740993");
  var list=json(post("library/favorites","{}",a));assertThat(list.path("favorites").path("total").asInt()).isEqualTo(1);
  assertThat(list.path("favorites").path("list").get(0).path("songId").asString()).isEqualTo("1");
  assertThat(post("library/favorite","{\"songId\":\"2\"}",a).statusCode()).isEqualTo(404);
  assertThat(post("library/song","{\"songId\":\"2\"}",null).statusCode()).isEqualTo(404);
  assertThat(post("library/favorites","{}",null).statusCode()).isEqualTo(401);
  for(int i=0;i<3;i++) json(post("library/unfavorite","{\"songId\":\"1\"}",a));
  assertThat(jdbc.queryForObject("SELECT favorite_count FROM songs WHERE id=1",Integer.class)).isZero();
 }
 @Test void concurrentFavoritesAndFailureRollback() throws Exception {
  String a=login().path("tokens").path("accessToken").asString();
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(6)) {
   var futures=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
   for(int i=0;i<12;i++) futures.add(pool.submit(()->post("library/favorite","{\"songId\":\"1\"}",a).statusCode()));
   for(var future:futures) assertThat(future.get()).isEqualTo(200);
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM song_likes",Long.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT favorite_count FROM songs WHERE id=1",Integer.class)).isEqualTo(1);
  json(post("library/unfavorite","{\"songId\":\"1\"}",a));
  jdbc.execute("ALTER TABLE songs ADD CONSTRAINT fail_counter CHECK(favorite_count=0)");
  assertThat(post("library/favorite","{\"songId\":\"1\"}",a).statusCode()).isEqualTo(500);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM song_likes",Long.class)).isZero();
 }
 @Test void secondUserCannotReadFirstUsersFavorites() throws Exception {
  String a=login().path("tokens").path("accessToken").asString();json(post("library/favorite","{\"songId\":\"1\"}",a));
  jdbc.update("INSERT INTO users(id,username,password,email) VALUES(2,'second',?,'second@example.test')",PasswordUtil.hash("password-123"));
  String second=json(post("session/login","{\"email\":\"second@example.test\",\"password\":\"password-123\"}",null)).path("tokens").path("accessToken").asString();
  assertThat(json(post("library/favorites","{\"userId\":\"9007199254740993\"}",second)).path("favorites").path("total").asInt()).isZero();
  assertThat(json(post("library/states","{\"songIds\":\"1,2\"}",second)).path("states").path("1").asBoolean()).isFalse();
 }
 @Test void malformedCredentialsAndExpiredSessionFailClosed() throws Exception {
  assertThat(post("session/login","{}",null).statusCode()).isEqualTo(401);
  assertThat(post("session/login","{",null).statusCode()).isEqualTo(400);
  String a=login().path("tokens").path("accessToken").asString();
  jdbc.update("UPDATE auth_sessions SET expires_at=0");
  assertThat(post("session/profile","{}",a).statusCode()).isEqualTo(401);
 }
 @Test void legacyMd5LoginUpgradesPasswordAndCreatesPersistentSession() throws Exception {
  String legacy=com.hope.music.auth.util.MD5EncryptUtil.md5("password-123");
  jdbc.update("UPDATE users SET password=? WHERE email='tester@example.test'",legacy);
  var response=login();
  String access=response.path("tokens").path("accessToken").asString();
  String stored=jdbc.queryForObject("SELECT password FROM users WHERE email='tester@example.test'",String.class);
  assertThat(stored).startsWith("$2a$");
  assertThat(PasswordUtil.verify("password-123",stored)).isTrue();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_access_tokens",Integer.class)).isEqualTo(1);
  assertThat(post("session/profile","{}",access).statusCode()).isEqualTo(200);
  assertThat(post("session/login","{\"email\":\"tester@example.test\",\"password\":\"incorrect\"}",null).statusCode()).isEqualTo(401);
 }
}
