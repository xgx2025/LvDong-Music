package servlet;

import junit.framework.TestCase;
import service.MusicService;
import utils.JwtTokenUtil;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class HlsPublicationRegressionTest extends TestCase {
    public void testWithdrawnSongCannotUseCachedMedia() throws Exception {
        HlsStreamingServlet servlet = new HlsStreamingServlet();
        servlet.musicService = (MusicService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MusicService.class}, (p, m, a) -> null);
        String access = JwtTokenUtil.generateAccessToken(new HashMap<>(Map.of("id", "1003")));
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> "Bearer " + access);
        AtomicInteger status = new AtomicInteger();
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (p, m, a) -> {
                    if (m.getName().equals("sendError")) status.set((Integer) a[0]);
                    return null;
                });
        // No initialized storage directory: denial must happen before filesystem access.
        servlet.serveHls("1", "index.m3u8", request, response);
        assertEquals(404, status.get());
        servlet.destroy();
    }
}
