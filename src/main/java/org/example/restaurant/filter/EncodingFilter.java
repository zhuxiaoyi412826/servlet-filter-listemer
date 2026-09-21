package org.example.restaurant.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 字符编码过滤器：统一 UTF-8，解决中文（菜品名、用户名）乱码问题。
 */
public class EncodingFilter implements Filter {

    private String encoding = "UTF-8";

    @Override
    public void init(FilterConfig config) {
        String e = config.getInitParameter("encoding");
        if (e != null && !e.isBlank()) encoding = e.trim();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding(encoding);
        response.setCharacterEncoding(encoding);
        if (request instanceof HttpServletRequest req && response instanceof HttpServletResponse resp) {
            String uri = req.getRequestURI();
            if (uri.startsWith("/api")) {
                // 接口统一 JSON 响应，避免浏览器解析 MIME 类型提示
                resp.setContentType("application/json;charset=" + encoding);
            } else {
                String lower = uri.toLowerCase();
                if (lower.endsWith(".html")) {
                    resp.setContentType("text/html;charset=" + encoding);
                } else if (lower.endsWith(".js")) {
                    resp.setContentType("application/javascript;charset=" + encoding);
                } else if (lower.endsWith(".css")) {
                    resp.setContentType("text/css;charset=" + encoding);
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // no-op
    }
}
