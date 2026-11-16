package servlet;

import junit.framework.TestCase;
import pojo.SearchResult;
import service.SearchService;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchRegressionTest extends TestCase {
    public void testInvalidTokenFallsBackToAnonymous() throws Exception {
        SearchServlet servlet = new SearchServlet();
        AtomicBoolean called = new AtomicBoolean();
        servlet.searchService = (SearchService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SearchService.class}, (proxy, method, args) -> {
                    assertNull(args[3]);
                    assertEquals(1, args[1]);
                    called.set(true);
                    return new SearchResult();
                });
        Map<?, ?> result = (Map<?, ?>) servlet.search("invalid-token", "song", 0, 20);
        assertEquals(true, result.get("success"));
        assertTrue(called.get());
    }
}
