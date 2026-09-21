package org.example.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import org.example.restaurant.model.User;
import org.example.restaurant.util.DbUtil;

/** 登录辅助数据：手机验证码 + 记住我令牌。 */
public final class AuthDao {

    private AuthDao() {
    }

    // ==================== 手机验证码 ====================

    /** 保存手机号验证码（同一手机号旧验证码自动失效）。 */
    public static void savePhoneCode(String phone, String code, int expireMinutes) throws SQLException {
        String invalidate = "UPDATE t_phone_code SET used = 1 WHERE phone = ? AND used = 0";
        String insert = "INSERT INTO t_phone_code(phone,code,expire_at,used,created_at) VALUES(?,?,?,0,NOW())";
        try (Connection c = DbUtil.open()) {
            try (PreparedStatement ps = c.prepareStatement(invalidate)) {
                ps.setString(1, phone);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, phone);
                ps.setString(2, code);
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(expireMinutes)));
                ps.executeUpdate();
            }
        }
    }

    /** 校验验证码（60 秒内有效且未使用），校验通过后置为已使用。 */
    public static boolean verifyPhoneCode(String phone, String code) throws SQLException {
        String sql = "SELECT id FROM t_phone_code WHERE phone = ? AND code = ? AND used = 0 AND expire_at >= NOW()"
                + " ORDER BY id DESC LIMIT 1";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                long id = rs.getLong("id");
                try (PreparedStatement up = c.prepareStatement("UPDATE t_phone_code SET used = 1 WHERE id = ?")) {
                    up.setLong(1, id);
                    up.executeUpdate();
                }
                return true;
            }
        }
    }

    public static int cleanExpiredCodes() throws SQLException {
        String sql = "DELETE FROM t_phone_code WHERE expire_at < NOW() OR used = 1";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    // ==================== 记住我令牌 ====================

    public static void saveToken(String token, long userId, int expireDays) throws SQLException {
        String sql = "INSERT INTO t_login_token(token,user_id,expire_at,created_at) VALUES(?,?,?,NOW())";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2, userId);
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(expireDays)));
            ps.executeUpdate();
        }
    }

    /** 依据 Cookie 中的令牌查找对应用户（已过期则视为无效）。 */
    public static User findUserByToken(String token) throws SQLException {
        String sql = "SELECT u.id,u.username,u.password_hash,u.salt,u.nickname,u.phone,u.avatar,u.address,"
                + "u.role,u.status,u.created_at"
                + " FROM t_login_token t JOIN t_user u ON u.id = t.user_id"
                + " WHERE t.token = ? AND t.expire_at >= NOW() LIMIT 1";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UserDao.map(rs) : null;
            }
        }
    }

    public static void removeToken(String token) throws SQLException {
        String sql = "DELETE FROM t_login_token WHERE token = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }
}
