package org.example.restaurant.listener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 请求监听器（ServletRequestListener）：PV 统计、慢请求发现与 Session 心跳刷新。
 */
public class RequestStatListener implements ServletRequestListener {

    /** 页面浏览量累计 */
    public static final AtomicLong PV = new AtomicLong();
    /** 按 URI 的访问次数 */
    public static final Map<String, AtomicLong> URI_STATS = new ConcurrentHashMap<>();
    /** 慢请求阈值（毫秒） */
    private static final long SLOW_THRESHOLD = 1000L;

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        HttpServletRequest req = (HttpServletRequest) sre.getServletRequest();
        PV.incrementAndGet();
        URI_STATS.computeIfAbsent(req.getRequestURI(), k -> new AtomicLong()).incrementAndGet();
        req.setAttribute("reqStartTime", System.currentTimeMillis());
        // 刷新会话最近活跃时间（配合 HttpSessionListener 做在线统计）
        HttpSession session = req.getSession(false);
        if (session != null) OnlineUserListener.touch(session);
        ServletContext ctx = req.getServletContext();
        ctx.setAttribute("pv", PV.get());
    }

    @Override
    public void requestDestroyed(ServletRequestEvent sre) {
        HttpServletRequest req = (HttpServletRequest) sre.getServletRequest();
        Object start = req.getAttribute("reqStartTime");
        if (start instanceof Long begin) {
            long cost = System.currentTimeMillis() - begin;
            if (cost > SLOW_THRESHOLD) {
                System.err.printf("[慢请求] %s 耗时 %dms @ %s%n",
                        req.getRequestURI(), cost, LocalDateTime.now());
            }
        }
    }
}
