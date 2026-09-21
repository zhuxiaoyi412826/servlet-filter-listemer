package org.example.restaurant.listener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.example.restaurant.model.User;
import org.example.restaurant.util.WebUtil;

/**
 * 会话监听器（HttpSessionListener）：在线人数与在线明细统计。
 * Map 结构：sessionId -> {user, firstSeen, lastSeen}
 */
public class OnlineUserListener implements ServletContextListener, HttpSessionListener {

    public static final String VISITOR = "游客";
    /** sessionId -> 在线信息 */
    public static final Map<String, Map<String, String>> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        ctx.setAttribute("onlineCount", 0);
        // 容器重启后旧会话由会话钝化机制处理，此处仅初始化计数
        System.out.println("[Listener] 在线人数统计已启动");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        SESSIONS.clear();
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        Map<String, String> info = new ConcurrentHashMap<>();
        info.put("user", VISITOR);
        info.put("sessionId", session.getId());
        info.put("firstSeen", LocalDateTime.now().toString());
        info.put("lastSeen", LocalDateTime.now().toString());
        info.put("ip", "");
        SESSIONS.put(session.getId(), info);
        // Session 创建后可能有 RememberMe 之外的渠道写入 IP，这里保留占位
        Object user = session.getAttribute(WebUtil.SESSION_USER);
        if (user instanceof User u) {
            info.put("user", u.displayName());
        }
        ServletContext ctx = session.getServletContext();
        ctx.setAttribute("onlineCount", SESSIONS.size());
        System.out.printf("[Listener] 会话创建 %s，当前在线 %d%n", session.getId(), SESSIONS.size());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        SESSIONS.remove(se.getSession().getId());
        se.getSession().getServletContext().setAttribute("onlineCount", SESSIONS.size());
        System.out.printf("[Listener] 会话销毁 %s，当前在线 %d%n", se.getSession().getId(), SESSIONS.size());
    }

    public static void touch(HttpSession session) {
        Map<String, String> info = SESSIONS.get(session.getId());
        if (info != null) info.put("lastSeen", LocalDateTime.now().toString());
    }

    public static int count() {
        return SESSIONS.size();
    }
}
