package servlet;

import junit.framework.TestCase;
import service.SingerService;
import service.MusicService;
import utils.JwtTokenUtil;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class UploadRegressionTest extends TestCase {
    public void testOnlyApprovedMusicianCanReachUpload() {
        for (int status : new int[]{0, 1, 2}) {
            MusicServlet servlet = new MusicServlet();
            servlet.singerService = (SingerService) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{SingerService.class}, (p, m, a) -> status);
            AtomicBoolean called = new AtomicBoolean();
            servlet.musicService = (MusicService) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{MusicService.class}, (p, m, a) -> { called.set(true); return true; });
            String token = JwtTokenUtil.generateAccessToken(new java.util.HashMap<>(Map.of("id", "1001")));
            Map<?, ?> result = (Map<?, ?>) servlet.upload(token, "song", "1001", "03:00", "album", null, null, null);
            assertEquals(status == 1, called.get());
            assertEquals(status == 1, result.get("success"));
        }
    }
}
