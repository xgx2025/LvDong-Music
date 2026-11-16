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
