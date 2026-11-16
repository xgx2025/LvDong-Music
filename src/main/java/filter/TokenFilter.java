package filter;

import utils.JwtTokenUtil;
import utils.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class TokenFilter implements Filter {

    Set<String> publicHtmlPaths = new HashSet<>(Arrays.asList(
            "/login.html", "/register.html", "/password-reset.html","/index.html","/home.html","/user-profile.html",
            "/player.html","/search.html","/upload-music.html","/comment.html",
            "/humming-recognition.html","/feedback.html","/ai-assistant.html","/qqLogin.html"
    ));

    // 不需要验证Token的路径
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/api/log-error"
    ));

    // 不需要验证Token的Servlet action
    private static final Set<String> EXCLUDED_ACTIONS = new HashSet<>(Arrays.asList(
           "serveHls", "login", "register", "refreshToken","validate","sendVerificationCode","logout","resetPassword",// 修正为正确的action名称
            "search","getStatus"
    ));



    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String excluded = filterConfig.getInitParameter("excludedPaths");
        if (excluded != null && !excluded.isEmpty()) {
            EXCLUDED_PATHS.addAll(Arrays.asList(excluded.split(",")));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 判断是否为无需验证的路径
        if (isExcludedRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 验证双Token
            validateTokens(httpRequest);

            // 验证通过，继续执行后续过滤器/业务逻辑
            chain.doFilter(request, response);
        } catch (TokenException e) {
            // 处理认证异常
            String acceptHeader = httpRequest.getHeader("Accept");
            boolean isHtmlRequest = acceptHeader != null && acceptHeader.contains("text/html");

            if (isHtmlRequest) {
                // HTML请求：重定向到登录页
                String loginUrl = httpRequest.getContextPath() + "/login.html";
                httpResponse.sendRedirect(loginUrl);
            } else {
                // API请求：返回JSON错误
                sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            }
        } catch (Exception e) {
            // 处理其他异常
            String acceptHeader = httpRequest.getHeader("Accept");
            boolean isHtmlRequest = acceptHeader != null && acceptHeader.contains("text/html");

            if (isHtmlRequest) {
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.html");
            } else {
                sendErrorResponse(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
            }
        }
    }

    @Override
    public void destroy() {
        // 释放资源
    }


    /**
     * 判断请求是否需要排除Token验证
     */
    private boolean isExcludedRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String action = request.getParameter("action");

        boolean isWebSocketHandshake = path.equals("/websocket")
                && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));
        if (isWebSocketHandshake) {
            return true; // 放行WebSocket握手请求
        }

        // 放行静态资源目录
        if (path.startsWith("/static/") || path.startsWith("/css/") || path.startsWith("/js/") ||
                path.startsWith("/images/") || path.startsWith("/fonts/") || path.startsWith("/pages/")) {
            return true;
        }

        if (path.endsWith(".ts")) {
            return true;
        }

        // 放行公开的HTML页面
        if (path.equals("/") || publicHtmlPaths.contains(path)) {
            return true;
        }

        // 精确匹配路径
        if (EXCLUDED_PATHS.contains(path)) {
            return true;
        }

        // 支持通配符匹配
        for (String pattern : EXCLUDED_PATHS) {
            if (pattern.endsWith("**") && path.startsWith(pattern.substring(0, pattern.length() - 2))) {
                return true;
            }
        }

        // Servlet action匹配
        if (action != null && EXCLUDED_ACTIONS.contains(action)) {
            return path.endsWith("/userServlet") || path.endsWith("/authServlet") || path.endsWith("/searchServlet") || path.endsWith("/hls");
        }

        return false;
    }

    /**
     * 验证双Token（仅验证有效性，不自动刷新，由前端处理刷新逻辑）
     */
    private void validateTokens(HttpServletRequest request) throws TokenException {
        // 提取并验证 accessToken
        String accessToken = extractAccessToken(request);
        if (!isValidAccessToken(accessToken)) {
            // accessToken 无效（过期或无效），直接抛出异常（最终返回 401）
            throw new TokenException("access_token 无效或已过期");
        }
    }
    /**
     * 提取access_token（从Authorization头的Bearer格式提取）
     */
    private String extractAccessToken(HttpServletRequest request) throws TokenException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new TokenException("access_token缺失或格式错误（应为Bearer xxx）");
        }
        return authHeader.substring(7).trim(); // 去除"Bearer "前缀
    }

    /**
     * 验证access_token是否有效（未过期+签名正确）
     */
    private boolean isValidAccessToken(String accessToken) throws TokenException {
        try {
            // 检查token是否在黑名单中（已登出）
            if (TokenBlacklist.contains(accessToken)) {
                return false;
            }
            boolean isExpired = JwtTokenUtil.isTokenExpired(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
            return !isExpired;
        } catch (SignatureException e) {
            throw new TokenException("access_token签名无效");
        } catch (ExpiredJwtException e) {
            return false;
        }
    }


    /**
     * 验证refresh_token是否有效
     */
    private boolean isValidRefreshToken(String refreshToken) throws TokenException {
        try {
            return !JwtTokenUtil.isTokenExpired(refreshToken, JwtTokenUtil.REFRESH_TOKEN_SECRET);
        } catch (SignatureException e) {
            throw new TokenException("refresh_token签名无效");
        } catch (ExpiredJwtException e) {
            throw new TokenException("refresh_token已过期，请重新登录");
        } catch (Exception e) {
            throw new TokenException("refresh_token格式错误");
        }
    }

    /**
     * 基于refresh_token生成新的access_token（复用refresh_token中的用户信息）
     */
    private String generateNewAccessToken(Claims refreshClaims) {
        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("id", refreshClaims.get("id"));
        newClaims.put("username", refreshClaims.get("username"));
        return JwtTokenUtil.generateAccessToken(newClaims);
    }

    /**
     * 发送JSON格式的错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
        out.flush();
    }

    /**
     * 自定义Token异常（区分不同错误类型）
     */
    public static class TokenException extends Exception {
        public TokenException(String message) {
            super(message);
        }
    }
}
