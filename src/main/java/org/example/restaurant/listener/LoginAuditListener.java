package org.example.restaurant.listener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionAttributeListener;
import org.example.restaurant.model.User;
import org.example.restaurant.util.WebUtil;

/**
 * 会话属性监听器（HttpSessionAttributeListener）：
 * 监听 SESSION_USER 的写入 / 覆盖 / 移除，完成登录审计并更新在线用户名。
 */
public class LoginAuditListener implements HttpSessionAttributeListener {

    /** 审计日志：sessionId -> 记录 */
    public static final Map<String, String> AUDIT = new ConcurrentHashMap<>();

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        if (!WebUtil.SESSION_USER.equals(event.getName())) return;
        Object value = event.getValue();
        String name = value instanceof User u ? u.displayName() : String.valueOf(value);
        Map<String, String> info = OnlineUserListener.SESSIONS.get(event.getSession().getId());
        if (info != null) info.put("user", name);
        String log = String.format("%s 登录 [session=%s]", name, event.getSession().getId());
        AUDIT.put(event.getSession().getId() + "#LOGIN", LocalDateTime.now() + " " + log);
        System.out.println("[Audit] " + log);
    }

    @Override
    public void attributeRemoved(HttpSessionBindingEvent event) {
        if (!WebUtil.SESSION_USER.equals(event.getName())) return;
        Map<String, String> info = OnlineUserListener.SESSIONS.get(event.getSession().getId());
        String name = info == null ? "游客" : info.getOrDefault("user", "游客");
        if (info != null) info.put("user", OnlineUserListener.VISITOR);
        String log = name + " 退出登录 [session=" + event.getSession().getId() + "]";
        AUDIT.put(event.getSession().getId() + "#LOGOUT", LocalDateTime.now() + " " + log);
        System.out.println("[Audit] " + log);
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        attributeAdded(event);
    }
}
