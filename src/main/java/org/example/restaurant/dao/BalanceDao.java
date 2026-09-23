package org.example.restaurant.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurant.model.BalanceLog;
import org.example.restaurant.util.DbUtil;

/**
 * 余额与流水的数据访问层：只负责 SQL，不含业务规则与事务边界（事务由 BalanceService 控制）。
 * <p>
 * 扣款路径使用悲观锁：{@code SELECT balance FROM t_user WHERE id = ? FOR UPDATE}，
 * 事务提交前其它会话无法读写该行，避免并发扣款导致余额被击穿。
 */
public final class BalanceDao {

    private BalanceDao() {
    }

    /**
     * 悲观锁读余额：必须在事务内（autoCommit=false）调用，锁在 commit/rollback 时释放。
     *
     * @throws IllegalArgumentException 用户不存在
     */
    public static BigDecimal lockBalance(Connection c, long userId) throws SQLException {
        String sql = "SELECT balance FROM t_user WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("用户不存在");
                return rs.getBigDecimal("balance");
            }
        }
    }

    /** 普通读余额（不加锁，用于展示）。 */
    public static BigDecimal getBalance(long userId) throws SQLException {
        String sql = "SELECT balance FROM t_user WHERE id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("用户不存在");
                return rs.getBigDecimal("balance");
            }
        }
    }

    /** 写回余额（配合 lockBalance 使用）。 */
    public static void updateBalance(Connection c, long userId, BigDecimal balance) throws SQLException {
        String sql = "UPDATE t_user SET balance = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, balance);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /** 写一条流水。 */
    public static void insertLog(Connection c, BalanceLog log) throws SQLException {
        String sql = "INSERT INTO t_balance_log(user_id,change_amount,balance_after,type,remark,created_at)"
                + " VALUES(?,?,?,?,?,NOW())";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, log.userId);
            ps.setBigDecimal(2, BigDecimal.valueOf(log.changeAmount).setScale(2, java.math.RoundingMode.HALF_UP));
            ps.setBigDecimal(3, BigDecimal.valueOf(log.balanceAfter).setScale(2, java.math.RoundingMode.HALF_UP));
            ps.setString(4, log.type);
            ps.setString(5, log.remark);
            ps.executeUpdate();
        }
    }

    /** 流水分页（按时间倒序：最新在前）。 */
    public static List<BalanceLog> findLogs(long userId, int limit, int offset) throws SQLException {
        String sql = "SELECT id,user_id,change_amount,balance_after,type,remark,created_at"
                + " FROM t_balance_log WHERE user_id = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, Math.max(limit, 1));
            ps.setInt(3, Math.max(offset, 0));
            try (ResultSet rs = ps.executeQuery()) {
                List<BalanceLog> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public static int countLogs(long userId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM t_balance_log WHERE user_id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** 累计支出与累计收入（正数）。 */
    public static Map<String, Object> summary(long userId) throws SQLException {
        String sql = "SELECT"
                + " IFNULL(SUM(CASE WHEN change_amount < 0 THEN -change_amount ELSE 0 END),0) AS spent,"
                + " IFNULL(SUM(CASE WHEN change_amount > 0 THEN change_amount ELSE 0 END),0) AS income"
                + " FROM t_balance_log WHERE user_id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("totalSpent", rs.getBigDecimal("spent").doubleValue());
                m.put("totalIncome", rs.getBigDecimal("income").doubleValue());
                return m;
            }
        }
    }

    /** 是否已有流水（独立连接版，用于快速判断）。 */
    public static boolean hasLog(long userId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM t_balance_log WHERE user_id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** 是否已有流水（事务内版，配合悲观锁做幂等初始化）。 */
    public static boolean hasLog(Connection c, long userId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM t_balance_log WHERE user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static BalanceLog map(ResultSet rs) throws SQLException {
        BalanceLog log = new BalanceLog();
        log.id = rs.getLong("id");
        log.userId = rs.getLong("user_id");
        log.changeAmount = rs.getBigDecimal("change_amount").doubleValue();
        log.balanceAfter = rs.getBigDecimal("balance_after").doubleValue();
        log.type = rs.getString("type");
        log.remark = rs.getString("remark");
        Timestamp ts = rs.getTimestamp("created_at");
        log.createdAt = ts == null ? null : ts.toLocalDateTime();
        return log;
    }
}
