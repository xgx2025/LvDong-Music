package filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 安全响应头。
 *
 * 说明：页面目前大量使用内联 <script>/onclick 与 Tailwind CDN，
 * 因此 CSP 暂时保留 'unsafe-inline' / 'unsafe-eval'，主要作用是
 * 限制可加载的脚本与数据来源，等前端工程化改造后再收紧。
 */
public class SecurityHeadersFilter implements Filter {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://cdn.tailwindcss.com https://cdnjs.cloudflare.com",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdn.tailwindcss.com https://fonts.googleapis.com",
            "img-src 'self' data: blob: https:",
            "media-src 'self' data: blob:",
            "font-src 'self' data: https://cdn.jsdelivr.net https://fonts.gstatic.com",
            "connect-src 'self' ws: wss:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'"
    );

    private static final String PERMISSIONS_POLICY =
            "geolocation=(), camera=(), microphone=(self), payment=()";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Referrer-Policy", "no-referrer");
        httpResponse.setHeader("Permissions-Policy", PERMISSIONS_POLICY);

        // HSTS 只在 HTTPS 下发送，避免本地 http 调试被浏览器记住
        if (httpRequest.isSecure()) {
            httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
