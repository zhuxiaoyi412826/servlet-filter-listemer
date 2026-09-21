package org.example.restaurant.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.restaurant.model.Cart;
import org.example.restaurant.model.User;

/** Web 请求/响应通用工具：参数读取、JSON 输出、Cookie、Session 取值。 */
public final class WebUtil {

    public static final String SESSION_USER = "SESSION_USER";
    public static final String SESSION_CART = "SESSION_CART";
    public static final String COOKIE_REMEMBER = "rm_token";
    public static final String COOKIE_HISTORY = "dish_history";

    private WebUtil() {
    }

    // ===================== 输入 =====================

    /** 读取 JSON 请求体字符串。 */
    public static String readBody(HttpServletRequest req) throws IOException {
        try (InputStream in = req.getInputStream()) {
            byte[] buf = in.readAllBytes();
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    /**
     * 统一参数入口：application/json 请求体 + query/form 参数合并为一个 Map。
     */
    public static Map<String, Object> params(HttpServletRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, String[]> pm = req.getParameterMap();
        if (pm != null) {
            pm.forEach((k, v) -> {
                if (v != null && v.length > 0) map.put(k, v.length == 1 ? v[0] : v);
            });
        }
        String ct = req.getContentType();
        if (ct != null && ct.toLowerCase().contains("application/json")) {
            try {
                String body = readBody(req);
                if (body != null && !body.isBlank()) {
                    Object parsed = Json.parse(body);
                    if (parsed instanceof Map<?, ?> m) {
                        m.forEach((k, v) -> map.put(String.valueOf(k), v));
                    }
                }
            } catch (Exception e) {
                // 非法 JSON 时忽略，退化为使用 query/form 参数
            }
        }
        return map;
    }

    public static String str(Map<String, Object> p, String key) {
        return str(p, key, "");
    }

    public static String str(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        if (v == null) return def;
        if (v instanceof String s) return s;
        if (v instanceof String[] arr) return arr.length > 0 ? arr[0] : def;
        return String.valueOf(v);
    }

    public static int intOf(Map<String, Object> p, String key, int def) {
        Object v = p.get(key);
        if (v == null) return def;
        try {
            if (v instanceof Number n) return n.intValue();
            return Integer.parseInt(str(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static long longOf(Map<String, Object> p, String key, long def) {
        Object v = p.get(key);
        if (v == null) return def;
        try {
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(str(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static double doubleOf(Map<String, Object> p, String key, double def) {
        Object v = p.get(key);
        if (v == null) return def;
        try {
            if (v instanceof Number n) return n.doubleValue();
            return Double.parseDouble(str(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean boolOf(Map<String, Object> p, String key, boolean def) {
        Object v = p.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = str(v);
        return s.equals("true") || s.equals("1") || s.equals("on") || s.equals("yes");
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    // ===================== 输出 =====================

    public static void json(HttpServletResponse resp, Object data) {
        json(resp, 200, data);
    }

    public static void json(HttpServletResponse resp, int httpStatus, Object data) {
        try {
            resp.setStatus(httpStatus);
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("application/json;charset=UTF-8");
            // 禁止缓存，保证回显数据永远是最新的
            resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            resp.setHeader("Pragma", "no-cache");
            resp.getWriter().write(Json.toJson(data));
            resp.getWriter().flush();
        } catch (IOException ignored) {
        }
    }

    public static void fail(HttpServletResponse resp, int httpStatus, String msg) {
        json(resp, httpStatus, Resp.fail(msg));
    }

    // ===================== Cookie =====================

    public static String cookie(HttpServletRequest req, String name) {
        Cookie[] cs = req.getCookies();
        if (cs == null) return null;
        for (Cookie c : cs) {
            if (c.getName().equals(name) && c.getValue() != null && !c.getValue().isEmpty()) {
                return c.getValue();
            }
        }
        return null;
    }

    public static void setCookie(HttpServletResponse resp, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);
    }

    public static void removeCookie(HttpServletResponse resp, String name) {
        setCookie(resp, name, "", 0);
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    // ===================== Session =====================

    public static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object o = session.getAttribute(SESSION_USER);
        return o instanceof User u ? u : null;
    }

    @SuppressWarnings("unchecked")
    public static Cart cart(HttpServletRequest req) {
        HttpSession session = req.getSession(true);
        Object o = session.getAttribute(SESSION_CART);
        if (o instanceof Cart c) return c;
        Cart cart = new Cart();
        session.setAttribute(SESSION_CART, cart);
        return cart;
    }

    public static String clientIp(HttpServletRequest req) {
        String x = req.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) return x.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    public static String now() {
        return LocalDateTime.now().toString();
    }
}
