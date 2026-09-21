package org.example.restaurant.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 极简 YAML 配置读取器（不引入 SnakeYAML 等第三方库，纯 JavaSE 实现）。
 *
 * 支持：
 * <pre>
 *   key: value
 *   ${ENV:defaultValue}   占位符，优先 System.getProperty -> System.getenv -> 默认值
 * </pre>
 */
public final class AppConfig {

    private static volatile AppConfig instance = new AppConfig(new LinkedHashMap<>());

    private final Map<String, String> props = new LinkedHashMap<>();
    /** 原始行 -> 便于 /api/health 回显当前生效配置（口令脱敏） */
    private final transient String source;

    private AppConfig(Map<String, String> props) {
        this.props.putAll(props);
        this.source = props.toString();
    }

    public static AppConfig me() {
        return instance;
    }

    /** 从 classpath 加载配置文件，解析成功后作为全局单例。 */
    public static AppConfig load(String classpathResource) {
        Map<String, String> parsed = new LinkedHashMap<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("未找到配置文件: " + classpathResource);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                Map<Integer, String> levelKeys = new TreeMap<>();
                String line;
                while ((line = br.readLine()) != null) {
                    String raw = line;
                    if (raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
                    int idx = raw.indexOf(':');
                    if (idx < 0) continue;
                    String key = raw.substring(0, idx).trim();
                    String value = raw.substring(idx + 1).trim();
                    // 行尾注释（# 前有空格才视为注释，避免误伤包含 # 的字符串）
                    int hash = value.indexOf(" #");
                    if (hash >= 0) value = value.substring(0, hash).trim();
                    int level = indentLevel(raw) / 2;
                    levelKeys.put(level, key);
                    StringBuilder full = new StringBuilder();
                    for (int i = 0; i <= level; i++) {
                        String part = levelKeys.get(i);
                        if (part == null) continue;
                        if (full.length() > 0) full.append('.');
                        full.append(part);
                    }
                    if (!value.isEmpty()) {
                        parsed.put(full.toString(), resolve(value));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取配置文件失败: " + classpathResource, e);
        }
        instance = new AppConfig(parsed);
        return instance;
    }

    private static int indentLevel(String line) {
        int n = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') n++;
            else break;
        }
        return n;
    }

    /** 解析 ${NAME:default} / ${NAME} 占位符。 */
    private static String resolve(String value) {
        if (value.indexOf('$') < 0) return value;
        StringBuilder sb = new StringBuilder(value);
        for (int guard = 0; guard < 20; guard++) {
            int start = sb.indexOf("${");
            if (start < 0) break;
            int end = sb.indexOf("}", start);
            if (end < 0) break;
            String expr = sb.substring(start + 2, end);
            String name, def = "";
            int colon = expr.indexOf(':');
            if (colon >= 0) {
                name = expr.substring(0, colon).trim();
                def = expr.substring(colon + 1);
            } else {
                name = expr.trim();
            }
            String env = System.getProperty(name);
            if (env == null || env.isEmpty()) env = System.getenv(name);
            if (env == null || env.isEmpty()) env = def;
            sb.replace(start, end + 1, env);
        }
        return sb.toString();
    }

    public String get(String key) {
        return get(key, "");
    }

    public String get(String key, String defaultValue) {
        String v = props.get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        String v = get(key);
        if (v == null || v.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = get(key);
        if (v == null || v.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(v.trim());
    }

    /** 回显所有配置（敏感字段脱敏）。 */
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        props.forEach((k, v) -> {
            String lower = k.toLowerCase();
            if (lower.contains("password") || lower.contains("secret") || lower.contains("access-key")) {
                out.put(k, v.isEmpty() ? "" : "******");
            } else {
                out.put(k, v);
            }
        });
        return out;
    }
}
