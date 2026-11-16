package servlet;

import junit.framework.TestCase;
import service.MusicService;
import utils.JwtTokenUtil;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class HlsDeliveryRegressionTest extends TestCase {
    private HlsStreamingServlet servlet;
    private Path root;
    private String token;
    private final String keyName = "key-" + UUID.randomUUID().toString().replace("-", "") + ".key";
    protected void setUp() throws Exception {
        root = Files.createTempDirectory(Path.of("target"), "hls-delivery-");
        Path song = Files.createDirectories(root.resolve("1"));
        Files.write(song.resolve(keyName), new byte[16]);
        Files.write(song.resolve("seg-test-00000.ts"), new byte[]{1,2,3});
        Files.writeString(song.resolve("index.m3u8"), "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"hls?action=serveHls&songId=1&fileName="
                + keyName + "\"\n#EXTINF:1,\nseg-test-00000.ts\n#EXT-X-ENDLIST\n");
        servlet = new HlsStreamingServlet();
        Field field = HlsStreamingServlet.class.getDeclaredField("hlsRootDir");
        field.setAccessible(true); field.set(servlet, root.toString());
        servlet.musicService = (MusicService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{MusicService.class}, (p,m,a) -> "approved.mp3");
        token = JwtTokenUtil.generateAccessToken(new HashMap<>(Map.of("id","7001")));
    }
    protected void tearDown() { servlet.destroy(); }
    private HttpServletRequest request() {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (p,m,a) -> {
            if (m.getName().equals("getParameter") && a[0].equals("token")) return token;
            return null;
        });
    }
    public void testBootSharedSessionRevokesManifestKeyAndSegment() throws Exception {
        token = "b1a_" + "x".repeat(43);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try (var conn=utils.DatabaseUtil.getConnection(); var sql=conn.createStatement()) {
            assertTrue(conn.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
            sql.execute("CREATE TABLE IF NOT EXISTS auth_sessions(id VARCHAR(36) PRIMARY KEY,user_id BIGINT,refresh_hash CHAR(64),expires_at BIGINT,revoked INT)");
            sql.execute("CREATE TABLE IF NOT EXISTS auth_access_tokens(token_hash CHAR(64) PRIMARY KEY,session_id VARCHAR(36),expires_at BIGINT)");
            sql.execute("DELETE FROM auth_access_tokens"); sql.execute("DELETE FROM auth_sessions");
            sql.execute("INSERT INTO auth_sessions VALUES('test-session',7001,'unused',"+(System.currentTimeMillis()+60000)+",0)");
            try(var insert=conn.prepareStatement("INSERT INTO auth_access_tokens VALUES(?,'test-session',?)")) {
                insert.setString(1,hash);insert.setLong(2,System.currentTimeMillis()+60000);insert.executeUpdate();
            }
            assertEquals("7001",JwtTokenUtil.getClaimsFromToken(token,JwtTokenUtil.ACCESS_TOKEN_SECRET).get("id"));
            testNativeManifestUsesAuthenticatedSegmentAndKeyUrls();
            testEncryptionKeyIsServedWithNoStore();
            try { new UserServlet().refreshToken(token);fail("Boot access must never mint legacy JWTs"); }
            catch(IllegalArgumentException expected) { }
            HttpServletRequest logout=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpServletRequest.class},
                (p,m,a)->m.getName().equals("getHeader")?"Bearer "+token:null);
            assertTrue(((com.google.gson.JsonObject)new UserServlet().logout(logout,null)).get("success").getAsBoolean());
            for (String file : List.of("index.m3u8",keyName,"seg-test-00000.ts")) {
                int[] status={200};StringWriter body=new StringWriter();
                HttpServletResponse response=(HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)-> {
                    if(m.getName().equals("setStatus")) status[0]=(Integer)a[0];
                    if(m.getName().equals("getWriter")) return new PrintWriter(body);return null;
                });
                servlet.serveHls("1",file,request(),response);assertEquals(401,status[0]);
            }
            try { new dao.impl.BootSessionDao().validateAccess(token);fail("fresh validator must reject persisted revocation"); }
            catch(IllegalArgumentException expected) { }
        }
    }
    public void testNativeManifestUsesAuthenticatedSegmentAndKeyUrls() throws Exception {
        StringWriter text = new StringWriter();
        Map<String,String> headers = new HashMap<>();
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class}, (p,m,a) -> {
            if (m.getName().equals("getWriter")) return new PrintWriter(text);
            if (m.getName().equals("setHeader")) headers.put((String)a[0], (String)a[1]);
            return null;
        });
        servlet.serveHls("1", "index.m3u8", request(), response);
        assertTrue(text.toString().contains("fileName=seg-test-00000.ts&stream=1&token=" + token));
        assertTrue(text.toString().contains("fileName=" + keyName + "&stream=1&token=" + token));
        assertEquals("no-store", headers.get("Cache-Control"));
    }
    public void testEncryptionKeyIsServedWithNoStore() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Map<String,String> headers = new HashMap<>();
        ServletOutputStream stream = new ServletOutputStream() {
            public void write(int value) { bytes.write(value); }
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener listener) { }
        };
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class}, (p,m,a) -> {
            if (m.getName().equals("getOutputStream")) return stream;
            if (m.getName().equals("setHeader")) headers.put((String)a[0], (String)a[1]);
            return null;
        });
        servlet.serveHls("1", keyName, request(), response);
        assertEquals(16, bytes.size());
        assertEquals("no-store", headers.get("Cache-Control"));
    }
}
