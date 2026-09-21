package org.example.restaurant.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 JavaSE 实现的 JSON 解析 / 序列化工具（不依赖 Jackson / Fastjson 等框架）。
 */
public final class Json {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private Json() {
    }

    // ============================ 解析 ============================

    public static Object parse(String text) {
        if (text == null || text.isBlank()) return null;
        Parser p = new Parser(text);
        return p.value();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object o = parse(text);
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object value() {
            ws();
            char c = cur();
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        Map<String, Object> object() {
            expect("{");
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (cur() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                expect(":");
                m.put(k, value());
                ws();
                if (cur() == ',') { i++; continue; }
                expect("}");
                return m;
            }
        }

        List<Object> array() {
            expect("[");
            List<Object> list = new ArrayList<>();
            ws();
            if (cur() == ']') { i++; return list; }
            while (true) {
                list.add(value());
                ws();
                if (cur() == ',') { i++; continue; }
                expect("]");
                return list;
            }
        }

        String string() {
            expect("\"");
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        case '/': sb.append('/'); break;
                        case '\\': sb.append('\\'); break;
                        case '"': sb.append('"'); break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object number() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                boolean part = (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
                if (!part) break;
                i++;
            }
            String t = s.substring(start, i);
            if (t.isEmpty()) throw new IllegalArgumentException("非法 JSON 数字 @ " + i);
            if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) {
                return Double.parseDouble(t);
            }
            return Long.parseLong(t);
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        char cur() {
            if (i >= s.length()) throw new IllegalArgumentException("JSON 意外结束");
            return s.charAt(i);
        }

        void expect(String literal) {
            for (char c : literal.toCharArray()) {
                if (i >= s.length() || s.charAt(i) != c) {
                    throw new IllegalArgumentException("非法 JSON：期望 " + literal + " @ " + i);
                }
                i++;
            }
        }
    }

    // ============================ 序列化 ============================

    public static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o, new IdentityHashMap<>());
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o, IdentityHashMap<Object, Object> seen) {
        if (o == null) {
            sb.append("null");
            return;
        }
        if (seen.containsKey(o)) {          // 防止循环引用
            sb.append("null");
            return;
        }
        if (o instanceof String str) {
            quote(sb, str);
            return;
        }
        if (o instanceof Character c) {
            quote(sb, c.toString());
            return;
        }
        if (o instanceof Boolean b) {
            sb.append(b.booleanValue());
            return;
        }
        if (o instanceof BigDecimal dec) {
            sb.append(dec.setScale(2, RoundingMode.HALF_UP).toPlainString());
            return;
        }
        if (o instanceof Number n) {
            sb.append(n.toString());
            return;
        }
        if (o instanceof Date d) {
            quote(sb, DTF.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZONE)));
            return;
        }
        if (o instanceof Instant ins) {
            quote(sb, DTF.format(LocalDateTime.ofInstant(ins, ZONE)));
            return;
        }
        if (o instanceof LocalDateTime ldt) {
            quote(sb, DTF.format(ldt));
            return;
        }
        if (o instanceof Enum<?> e) {
            quote(sb, e.name());
            return;
        }
        seen.put(o, Boolean.TRUE);
        try {
            if (o instanceof Map<?, ?> map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> en : map.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    quote(sb, String.valueOf(en.getKey()));
                    sb.append(':');
                    write(sb, en.getValue(), seen);
                }
                sb.append('}');
                return;
            }
            if (o instanceof Iterable<?> it) {
                sb.append('[');
                boolean first = true;
                for (Object item : it) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, item, seen);
                }
                sb.append(']');
                return;
            }
            if (o.getClass().isArray()) {
                sb.append('[');
                Object[] arr = (Object[]) o;
                for (int k = 0; k < arr.length; k++) {
                    if (k > 0) sb.append(',');
                    write(sb, arr[k], seen);
                }
                sb.append(']');
                return;
            }
            // POJO：反射输出全部实例字段
            Map<String, Object> fields = new LinkedHashMap<>();
            for (Field f : o.getClass().getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                try {
                    f.setAccessible(true);
                    fields.put(f.getName(), f.get(o));
                } catch (Exception ignored) {
                    // 忽略不可访问字段
                }
            }
            write(sb, fields, seen);
        } finally {
            seen.remove(o);
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
