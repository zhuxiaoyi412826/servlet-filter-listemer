package org.example.restaurant.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 Ajax 响应结构：{ code:0, msg:"ok", data:{} }
 * code = 0 表示成功，非 0 表示业务失败（前端据此做错误回显）。
 */
public final class Resp {

    public static final int OK = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int ERROR = 500;

    private Resp() {
    }

    public static Map<String, Object> ok() {
        return ok(null);
    }

    public static Map<String, Object> ok(Object data) {
        return pack(OK, "ok", data);
    }

    public static Map<String, Object> fail(String msg) {
        return pack(ERROR, msg, null);
    }

    public static Map<String, Object> fail(int code, String msg) {
        return pack(code, msg, null);
    }

    public static Map<String, Object> fail(int code, String msg, Object data) {
        return pack(code, msg, data);
    }

    /** 列表数据 + 分页信息快捷封装。 */
    public static Map<String, Object> page(List<?> list, long total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", list);
        m.put("total", total);
        return ok(m);
    }

    private static Map<String, Object> pack(int code, String msg, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("msg", msg);
        m.put("data", data);
        m.put("timestamp", java.time.LocalDateTime.now().toString());
        return m;
    }
}
