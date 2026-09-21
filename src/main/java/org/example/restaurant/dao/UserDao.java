package org.example.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurant.model.User;
import org.example.restaurant.util.DbUtil;

/** 用户数据访问（原生 JDBC）。 */
public final class UserDao {

    private UserDao() {
    }

    public static User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.passwordHash = rs.getString("password_hash");
        u.salt = rs.getString("salt");
        u.nickname = rs.getString("nickname");
        u.phone = rs.getString("phone");
        u.avatar = rs.getString("avatar");
        u.address = rs.getString("address");
        u.role = rs.getString("role");
        u.status = rs.getInt("status");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        u.createdAt = ts == null ? null : ts.toLocalDateTime();
        return u;
    }

    public static User findById(long id) throws SQLException {
        String sql = "SELECT id,username,password_hash,salt,nickname,phone,avatar,address,role,status,created_at"
                + " FROM t_user WHERE id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public static User findByUsername(String username) throws SQLException {
        String sql = "SELECT id,username,password_hash,salt,nickname,phone,avatar,address,role,status,created_at"
                + " FROM t_user WHERE username = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public static User findByPhone(String phone) throws SQLException {
        String sql = "SELECT id,username,password_hash,salt,nickname,phone,avatar,address,role,status,created_at"
                + " FROM t_user WHERE phone = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 用户名或手机号登录 */
    public static User findByAccount(String account) throws SQLException {
        String sql = "SELECT id,username,password_hash,salt,nickname,phone,avatar,address,role,status,created_at"
                + " FROM t_user WHERE username = ? OR phone = ? LIMIT 1";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, account);
            ps.setString(2, account);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public static boolean exists(String column, String value) throws SQLException {
        String sql = "SELECT COUNT(1) FROM t_user WHERE " + column + " = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** 注册新用户，返回自增主键 */
    public static long insert(String username, String hash, String salt, String nickname,
                              String phone, String avatar, String address) throws SQLException {
        String sql = "INSERT INTO t_user(username,password_hash,salt,nickname,phone,avatar,address,role,created_at)"
                + " VALUES(?,?,?,?,?,?,?,'USER',NOW())";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, nickname);
            ps.setString(5, phone == null || phone.isEmpty() ? null : phone);
            ps.setString(6, avatar == null ? "" : avatar);
            ps.setString(7, address);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return 0;
        }
    }

    /** 更新资料：昵称、手机号、地址、头像 */
    public static int updateProfile(User u) throws SQLException {
        String sql = "UPDATE t_user SET nickname=?, phone=?, address=?, avatar=? WHERE id=?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.nickname);
            ps.setString(2, u.phone == null || u.phone.isEmpty() ? null : u.phone);
            ps.setString(3, u.address);
            ps.setString(4, u.avatar);
            ps.setLong(5, u.id);
            return ps.executeUpdate();
        }
    }

    public static int updateAvatar(long id, String avatarUrl) throws SQLException {
        String sql = "UPDATE t_user SET avatar=? WHERE id=?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, avatarUrl);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    public static List<User> findAll() throws SQLException {
        String sql = "SELECT id,username,password_hash,salt,nickname,phone,avatar,address,role,status,created_at FROM t_user";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<User> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        }
    }

    // ==================== 后台：用户管理 ====================

    /** 组装后台用户行（含订单数、累计消费，直接 JSON 输出给前端）。 */
    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("username", rs.getString("username"));
        m.put("nickname", rs.getString("nickname"));
        m.put("phone", rs.getString("phone"));
        m.put("avatar", rs.getString("avatar"));
        m.put("address", rs.getString("address"));
        m.put("role", rs.getString("role"));
        m.put("status", rs.getInt("status"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        m.put("createdAt", ts == null ? null : ts.toLocalDateTime());
        m.put("orderCount", rs.getLong("order_count"));
        m.put("totalSpend", rs.getBigDecimal("total_spend").doubleValue());
        java.sql.Timestamp last = rs.getTimestamp("last_order_at");
        m.put("lastOrderAt", last == null ? null : last.toLocalDateTime());
        return m;
    }

    private static String adminWhere(String keyword, String role, Integer status, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1=1 ");
        if (keyword != null && !keyword.isBlank()) {
            w.append(" AND (u.username LIKE ? OR u.nickname LIKE ? OR u.phone LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (role != null && !role.isBlank()) {
            w.append(" AND u.role = ?");
            args.add(role.trim().toUpperCase());
        }
        if (status != null) {
            w.append(" AND u.status = ?");
            args.add(status);
        }
        return w.toString();
    }

    /** 后台用户列表（含消费统计）。 */
    public static List<Map<String, Object>> searchForAdmin(String keyword, String role, Integer status,
                                                           int limit, int offset) throws SQLException {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(keyword, role, status, args);
        String sql = "SELECT u.id,u.username,u.nickname,u.phone,u.avatar,u.address,u.role,u.status,u.created_at,"
                + " (SELECT COUNT(1) FROM t_order o WHERE o.user_id = u.id) AS order_count,"
                + " (SELECT IFNULL(SUM(o.total_amount),0) FROM t_order o"
                + "   WHERE o.user_id = u.id AND o.status <> 'CANCELLED') AS total_spend,"
                + " (SELECT MAX(o.created_at) FROM t_order o WHERE o.user_id = u.id) AS last_order_at"
                + " FROM t_user u" + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object a : args) ps.setObject(i++, a);
            ps.setInt(i++, Math.max(limit, 1));
            ps.setInt(i, Math.max(offset, 0));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) list.add(row(rs));
                return list;
            }
        }
    }

    /** 后台用户总数（与 searchForAdmin 条件一致）。 */
    public static int countForAdmin(String keyword, String role, Integer status) throws SQLException {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(keyword, role, status, args);
        String sql = "SELECT COUNT(1) FROM t_user u" + where;
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** 修改角色：USER / ADMIN。 */
    public static int updateRole(long id, String role) throws SQLException {
        String sql = "UPDATE t_user SET role = ? WHERE id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER");
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    /** 启用 / 禁用账号。 */
    public static int updateStatus(long id, int status) throws SQLException {
        String sql = "UPDATE t_user SET status = ? WHERE id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, status == 0 ? 0 : 1);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    /** 重置密码。 */
    public static int resetPassword(long id, String hash, String salt) throws SQLException {
        String sql = "UPDATE t_user SET password_hash = ?, salt = ? WHERE id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setLong(3, id);
            return ps.executeUpdate();
        }
    }

    /** 删除用户及其记住我令牌（订单保留，仅作为历史数据）。 */
    public static int deleteUser(long id) throws SQLException {
        try (Connection c = DbUtil.open()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM t_login_token WHERE user_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM t_user WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate();
            }
        }
    }

    /** 现存管理员数量，防止把最后一个管理员降级。 */
    public static long countAdmins() throws SQLException {
        String sql = "SELECT COUNT(1) FROM t_user WHERE role = 'ADMIN'";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 用户概览：总数、今日新增、管理员数、禁用数。 */
    public static Map<String, Object> stats() throws SQLException {
        String sql = "SELECT COUNT(1) AS total,"
                + " SUM(CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END) AS admins,"
                + " SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS disabled,"
                + " SUM(CASE WHEN DATE(created_at) = CURDATE() THEN 1 ELSE 0 END) AS today_new,"
                + " SUM(CASE WHEN created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS week_new"
                + " FROM t_user";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("total", rs.getLong("total"));
            m.put("admins", rs.getLong("admins"));
            m.put("disabled", rs.getLong("disabled"));
            m.put("todayNew", rs.getLong("today_new"));
            m.put("weekNew", rs.getLong("week_new"));
            return m;
        }
    }
}
