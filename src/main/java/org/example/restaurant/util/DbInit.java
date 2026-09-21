package org.example.restaurant.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.example.restaurant.config.AppConfig;
import org.example.restaurant.model.Dish;

/**
 * 数据库初始化：自动建库建表 + 写入演示数据（管理员账号、示例用户、菜品）。
 */
public final class DbInit {

    private DbInit() {
    }

    /** 执行初始化，返回错误信息（null 表示成功）。 */
    public static String init(AppConfig cfg) {
        try {
            ensureDatabase();
            executeScript("db/schema.sql");
            ensureColumns();
            seedUsers();
            seedDishes();
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("[DB] 初始化失败: " + msg);
            return msg;
        }
    }

    private static void ensureDatabase() throws SQLException {
        String db = DbUtil.dbName();
        if (db == null || db.isEmpty()) return;
        try (Connection c = DbUtil.openServer(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    /** 执行 classpath 下的 SQL 脚本。 */
    private static void executeScript(String resource) throws SQLException, IOException {
        String sql = loadResource(resource);
        if (sql == null) return;
        // 去掉行注释
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--") || t.startsWith("#")) continue;
            cleaned.append(t).append('\n');
        }
        try (Connection c = DbUtil.open(); Statement st = c.createStatement()) {
            for (String raw : cleaned.toString().split(";")) {
                String statement = raw.trim();
                if (statement.isEmpty()) continue;
                st.execute(statement);
            }
        }
    }

    /**
     * 兼容旧库：补齐后续版本新增的列。
     * 老表已存在时 CREATE TABLE IF NOT EXISTS 不会新增列，这里用 information_schema 判断后 ALTER。
     */
    private static void ensureColumns() {
        addColumnIfAbsent("t_user", "status",
                "TINYINT NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用' AFTER role");
    }

    private static void addColumnIfAbsent(String table, String column, String definition) {
        String check = "SELECT COUNT(1) FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        String db = DbUtil.dbName();
        if (db != null && !db.isEmpty()) {
            check = "SELECT COUNT(1) FROM information_schema.COLUMNS"
                    + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        }
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(check)) {
            int idx = 1;
            if (db != null && !db.isEmpty()) ps.setString(idx++, db);
            ps.setString(idx++, table);
            ps.setString(idx, column);
            boolean exists;
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                exists = rs.getInt(1) > 0;
            }
            if (!exists) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                    System.out.println("[DB] 已补齐列: " + table + "." + column);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] 补齐列失败 " + table + "." + column + ": " + e.getMessage());
        }
    }

    private static String loadResource(String resource) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        }
    }

    /** 初始化演示账号：admin/123456、tom/123456 */
    private static void seedUsers() throws SQLException {
        seedUser("admin", "123456", "超级管理员", "13800000000", "ADMIN", "餐厅总部 A 座 101");
        seedUser("tom", "123456", "Tom同学", "13911112222", "USER", "阳光小区 3 栋 502");
    }

    private static void seedUser(String username, String rawPwd, String nickname, String phone,
                                 String role, String address) throws SQLException {
        String check = "SELECT id FROM t_user WHERE username = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(check)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        String salt = PasswordUtil.salt();
        String sql = "INSERT INTO t_user(username,password_hash,salt,nickname,phone,avatar,address,role,created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,NOW())";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(rawPwd, salt));
            ps.setString(3, salt);
            ps.setString(4, nickname);
            ps.setString(5, phone);
            ps.setString(6, "");
            ps.setString(7, address);
            ps.setString(8, role);
            ps.executeUpdate();
        }
        System.out.println("[DB] 初始化演示账号: " + username + " / 123456");
    }

    private static void seedDishes() throws SQLException {
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(1) FROM t_dish");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) > 0) return;
        }
        List<Map<String, String>> seed = List.of(
                Map.of("name", "宫保鸡丁", "cat", "热菜", "price", "32.00", "desc", "鸡腿肉丁配油酥花生米，糊辣酸甜，经典川味", "img", "🍗"),
                Map.of("name", "麻婆豆腐", "cat", "热菜", "price", "26.00", "desc", "嫩豆腐配牛肉末，麻、辣、鲜、香、烫", "img", "🌶️"),
                Map.of("name", "红烧狮子头", "cat", "热菜", "price", "48.00", "desc", "手打肉圆慢炖两小时，入口即化", "img", "🍖"),
                Map.of("name", "清蒸鲈鱼", "cat", "热菜", "price", "68.00", "desc", "鲜活鲈鱼清蒸，佐以葱丝热油", "img", "🐟"),
                Map.of("name", "干锅花菜", "cat", "热菜", "price", "29.00", "desc", "有机花菜配腊肉，镬气十足", "img", "🥦"),
                Map.of("name", "拍黄瓜", "cat", "凉菜", "price", "16.00", "desc", "蒜香浓郁，清爽解腻", "img", "🥒"),
                Map.of("name", "口水鸡", "cat", "凉菜", "price", "38.00", "desc", "红油藤椒汁浇淋，皮爽肉嫩", "img", "🥗"),
                Map.of("name", "扬州炒饭", "cat", "主食", "price", "22.00", "desc", "虾仁火腿青豆，粒粒分明", "img", "🍚"),
                Map.of("name", "担担面", "cat", "主食", "price", "20.00", "desc", "芝麻酱香浓郁，微辣回甘", "img", "🍜"),
                Map.of("name", "香菇滑鸡汤", "cat", "汤羹", "price", "30.00", "desc", "文火慢煨三小时，鲜甜滋补", "img", "🍲"),
                Map.of("name", "鲜榨橙汁", "cat", "饮品", "price", "18.00", "desc", "当季鲜橙现榨，无额外加糖", "img", "🍹"),
                Map.of("name", "酸梅汤", "cat", "饮品", "price", "12.00", "desc", "乌梅山楂熬制，冰镇后风味更佳", "img", "🧋")
        );
        String sql = "INSERT INTO t_dish(name,category,price,stock,image_url,description,status,created_at)"
                + " VALUES(?,?,?,?,?,?,1,NOW())";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map<String, String> d : seed) {
                ps.setString(1, d.get("name"));
                ps.setString(2, d.get("cat"));
                ps.setBigDecimal(3, new java.math.BigDecimal(d.get("price")));
                ps.setInt(4, 100);
                ps.setString(5, d.get("img"));
                ps.setString(6, d.get("desc"));
                ps.executeUpdate();
            }
        }
        System.out.println("[DB] 初始化菜品数据: " + seed.size() + " 道");
    }

    /** 把 ResultSet 行映射成 Dish。 */
    public static Dish mapDish(ResultSet rs) throws SQLException {
        Dish d = new Dish();
        d.id = rs.getLong("id");
        d.name = rs.getString("name");
        d.category = rs.getString("category");
        d.price = rs.getBigDecimal("price").doubleValue();
        d.stock = rs.getInt("stock");
        d.imageUrl = rs.getString("image_url");
        d.description = rs.getString("description");
        d.status = rs.getInt("status");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        d.createdAt = ts == null ? null : ts.toLocalDateTime();
        return d;
    }
}
