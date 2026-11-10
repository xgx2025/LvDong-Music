package filter;

import utils.ConfigUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CorsFilter implements Filter {

    private Set<String> allowedOrigins;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String originsConfig = ConfigUtil.get("CORS_ALLOWED_ORIGINS", "cors.allowed.origins", "");
        allowedOrigins = new HashSet<>(Arrays.asList(originsConfig.split(",")));
        allowedOrigins.removeIf(s -> s == null || s.trim().isEmpty());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String origin = httpRequest.getHeader("Origin");

        // 设置CORS头
        if (origin != null && allowedOrigins.contains(origin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpResponse.setHeader("Vary", "Origin");
        }
        // 未匹配白名单时不设置 Access-Control-Allow-Origin，浏览器会拒绝跨域请求

        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        httpResponse.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Requested-With, Range");
        httpResponse.setHeader("Access-Control-Expose-Headers",
                "Content-Length, Content-Range, Date, Server");
        httpResponse.setHeader("Access-Control-Max-Age", "86400");

        // 预检请求直接返回200
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
