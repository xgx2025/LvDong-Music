package servlet;

import com.google.gson.JsonObject;
import junit.framework.TestCase;
import utils.JwtTokenUtil;
import utils.TokenBlacklist;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;

/** Exercises actual BaseServlet JSON binding and refresh revocation. */
public class SessionRegressionTest extends TestCase {
    public void testProfileUsesTokenIdentityAndNeverSerializesPassword() throws Exception {
        pojo.User profile = new pojo.User();
        profile.setId(2001L); profile.setUsername("profile-user"); profile.setEmail("user@example.test");
        profile.setPassword("must-never-be-returned");
        service.UserService service = (service.UserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{service.UserService.class}, (proxy, method, args) -> {
                    assertEquals("getProfile", method.getName());
                    assertEquals(2001L, args[0]);
                    return profile;
                });
        UserServlet servlet = new UserServlet();
        java.lang.reflect.Field field = UserServlet.class.getDeclaredField("userService");
        field.setAccessible(true); field.set(servlet, service);
        String access = JwtTokenUtil.generateAccessToken(new java.util.HashMap<>(Map.of("id", "2001")));
        JsonObject response = (JsonObject) servlet.getProfile(access);
        assertTrue(response.get("success").getAsBoolean());
        assertEquals("2001", response.getAsJsonObject("user").get("id").getAsString());
        assertFalse(response.toString().contains("password"));
        assertFalse(response.toString().contains("must-never-be-returned"));
    }
    public void testJsonLogoutRevokesRefreshAndAccessTokens() throws Exception {
        String access = JwtTokenUtil.generateAccessToken(new java.util.HashMap<>(Map.of("id", "1001")));
        String refresh = JwtTokenUtil.generateRefreshToken(new java.util.HashMap<>(Map.of("id", "1001")));
        UserServlet servlet = new UserServlet();
        JsonObject refreshed = servlet.refreshToken(refresh);
        assertEquals(refresh, refreshed.getAsJsonObject("tokens").get("refreshToken").getAsString());
        StringWriter response = new StringWriter();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getParameterNames": return Collections.enumeration(Collections.singleton("action"));
                        case "getParameter": return "action".equals(args[0]) ? "logout" : null;
                        case "getHeaderNames": return Collections.enumeration(Collections.singleton("Authorization"));
                        case "getHeader": return "Authorization".equalsIgnoreCase((String) args[0]) ? "Bearer " + access : null;
                        case "getContentType": return "application/json";
                        case "getReader": return new BufferedReader(new StringReader("{\"refreshToken\":\"" + refresh + "\"}"));
                        default: return null;
                    }
                });
        HttpServletResponse output = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) ->
                        "getWriter".equals(method.getName()) ? new PrintWriter(response) :
                                "isCommitted".equals(method.getName()) ? false : null);
        servlet.doPost(request, output);
        assertTrue(response.toString(), response.toString().contains("\"success\":true"));
        assertTrue(TokenBlacklist.contains(access));
        assertTrue(TokenBlacklist.contains(refresh));
        try {
            servlet.refreshToken(refresh);
            fail("revoked refresh must not issue new access tokens");
        } catch (IllegalArgumentException expected) {
            // Expected authentication failure.
        }
    }

    public void testMissingRefreshIsRejected() {
        try {
            new UserServlet().refreshToken(null);
            fail("missing refresh must fail");
        } catch (IllegalArgumentException expected) {
            // Expected authentication failure.
        }
    }

    public void testInvalidAccessDoesNotPreventRefreshRevocation() {
        String refresh = JwtTokenUtil.generateRefreshToken(new java.util.HashMap<>(Map.of("id", "1002")));
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> "Bearer invalid-access");
        JsonObject result = (JsonObject) new UserServlet().logout(request, refresh);
        assertTrue(result.get("success").getAsBoolean());
        assertTrue(TokenBlacklist.contains(refresh));
    }
}
