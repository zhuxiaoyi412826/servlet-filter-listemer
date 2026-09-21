package org.example.restaurant.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.example.restaurant.config.AppConfig;

/**
 * 原生 JDBC 连接工具（不使用连接池框架，直接使用 DriverManager）。
 * 每次业务操作短连接，用完即关。
 */
public final class DbUtil {

    private static String url;
    private static String user;
    private static String password;
    private static String serverUrl;   // 不带库名，用于 CREATE DATABASE
    private static String dbName;
    private static volatile boolean ready = false;

    private DbUtil() {
    }

// 这段是 DbUtil.init()——JDBC 初始化方法
    public static synchronized void init(AppConfig cfg) {
        if (ready) return;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL 驱动缺失: com.mysql.cj.jdbc.Driver", e);
        }
        url = cfg.get("db.url").trim();
        user = cfg.get("db.username", "root").trim();
        password = cfg.get("db.password").trim();
        dbName = extractDbName(url);
        serverUrl = extractServerUrl(url);
        ready = true;
    }

    /** 取业务数据库连接（已指定库名）。 */
    public static Connection open() throws SQLException {
        ensure();
        return DriverManager.getConnection(url, user, password);
    }

    /** 取未指定库名的连接，用于创建数据库。 */
    public static Connection openServer() throws SQLException {
        ensure();
        return DriverManager.getConnection(serverUrl, user, password);
    }

    public static String dbName() {
        return dbName;
    }

    public static boolean isReady() {
        return ready;
    }

    private static void ensure() {
        if (!ready) throw new IllegalStateException("DbUtil 尚未初始化，请检查 AppContextListener");
    }

    /** 从 jdbc url 中解析库名：.../restaurant?xx=yy -> restaurant */
    private static String extractDbName(String jdbcUrl) {
        String tail = jdbcUrl.substring("jdbc:mysql://".length());
        int slash = tail.indexOf('/');
        if (slash < 0) return null;
        String rest = tail.substring(slash + 1);
        int q = rest.indexOf('?');
        String name = (q >= 0 ? rest.substring(0, q) : rest).trim();
        return name.isEmpty() ? null : name;
    }

    /** 拼接不带库名的连接地址。 */
    private static String extractServerUrl(String jdbcUrl) {
        String head = jdbcUrl.substring(0, "jdbc:mysql://".length());
        String tail = jdbcUrl.substring("jdbc:mysql://".length());
        int slash = tail.indexOf('/');
        List<String> parts = new ArrayList<>();
        if (slash < 0) return jdbcUrl;
        String hostPart = tail.substring(0, slash);
        String after = tail.substring(slash);      // 含尾部的 /xxx?yyy
        int q = after.indexOf('?');
        if (q >= 0) parts.add(after.substring(q)); // 保留参数，如 ?serverTimezone=...
        return head + hostPart + "/" + String.join("", parts);
    }
}
