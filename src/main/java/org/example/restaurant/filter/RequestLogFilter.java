package org.example.restaurant.filter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.util.WebUtil;

/**
 * 访问日志过滤器：打印每个请求的 IP、URL、状态码与耗时，并统计请求总量。
 */
public class RequestLogFilter implements Filter {

    /** 累计请求数 */
    public static final AtomicLong TOTAL_REQUESTS = new AtomicLong();
    /** 请求耗时统计 */
    public static final Map<String, AtomicLong> STATS = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }
        long start = System.currentTimeMillis();
        String uri = req.getRequestURI();
        String method = req.getMethod();
        TOTAL_REQUESTS.incrementAndGet();
        STATS.computeIfAbsent(uri, k -> new AtomicLong()).incrementAndGet();
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            String user = "anonymous";
            Object u = req.getSession(false) == null ? null : req.getSession(false).getAttribute(WebUtil.SESSION_USER);
            if (u instanceof org.example.restaurant.model.User userObj) user = userObj.username;
            System.out.printf("[%s] %s %s %s ip=%s status=%d cost=%dms user=%s%n",
                    LocalDateTime.now(), method, uri,
                    req.getQueryString() == null ? "" : "?" + req.getQueryString(),
                    WebUtil.clientIp(req), resp.getStatus(), cost, user);
        }
    }
}
