package org.example.restaurant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurant.dao.BalanceDao;
import org.example.restaurant.model.BalanceLog;
import org.example.restaurant.util.DbUtil;

/**
 * 余额业务层：业务规则校验 + 事务边界（余额变动走悲观锁）。
 * <p>
 * 这一层不感知 HTTP（没有 request/response），也不自己拼业务 SQL（交给 BalanceDao）。
 * <p>
 * 变动来源：
 * <ul>
 *     <li>充值：{@link #recharge(long, BigDecimal, String)} —— 用户手动充值</li>
 *     <li>消费：{@link #payInTx(Connection, long, BigDecimal, String)} —— 下单时由 PayService 自动调用</li>
 *     <li>退款：{@link #refundInTx(Connection, long, BigDecimal, String)} —— 取消订单时由 PayService 调用</li>
 * </ul>
 */
public final class BalanceService {

    /** 每个账号的初始金币 */
    public static final BigDecimal INITIAL = new BigDecimal("1000.00");

    /** 单笔充值上限 */
    private static final BigDecimal MAX_RECHARGE = new BigDecimal("50000.00");

    private BalanceService() {
    }

    /**
     * 余额概览：当前余额 + 累计支出/收入 + 流水分页。
     *
     * @param page 从 1 开始
     */
    public static Map<String, Object> overview(long userId, int page, int size) throws SQLException {
        ensureInitialized(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int total = BalanceDao.countLogs(userId);
        int pages = (int) Math.ceil(total * 1.0 / safeSize);
        if (safePage > pages && pages > 0) safePage = pages;

        List<BalanceLog> logs = BalanceDao.findLogs(userId, safeSize, (safePage - 1) * safeSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", money(BalanceDao.getBalance(userId)));
        data.putAll(BalanceDao.summary(userId));
        data.put("list", logs);
        data.put("total", total);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("pages", Math.max(pages, 1));
        return data;
    }

    /**
     * 手动充值（事务 + 悲观锁）。
     *
     * @param amount 正数金额
     * @throws IllegalArgumentException 金额非法
     */
    public static Map<String, Object> recharge(long userId, BigDecimal amount, String remark) throws SQLException {
        BigDecimal value = normalize(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        if (value.compareTo(MAX_RECHARGE) > 0) {
            throw new IllegalArgumentException("单笔充值不能超过 " + MAX_RECHARGE.toPlainString());
        }
        if (remark != null && remark.length() > 100) {
            throw new IllegalArgumentException("备注不能超过 100 个字");
        }
        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                String text = (remark == null || remark.isBlank()) ? "余额充值" : remark.trim();
                BalanceLog log = change(c, userId, value, "RECHARGE", text);
                c.commit();
                System.out.printf("[余额] 用户 %d 充值 %s，余额 -> %s%n",
                        userId, value.toPlainString(), BigDecimal.valueOf(log.balanceAfter).toPlainString());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("balance", log.balanceAfter);
                data.put("amount", log.changeAmount);
                data.put("log", log);
                return data;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** 下单支付（事务内调用）：扣减余额并写 SPEND 流水。 */
    static BalanceLog payInTx(Connection c, long userId, BigDecimal amount, String remark) throws SQLException {
        return change(c, userId, amount.negate(), "SPEND", remark);
    }

    /** 取消订单退款（事务内调用）：退回余额并写 REFUND 流水。 */
    static BalanceLog refundInTx(Connection c, long userId, BigDecimal amount, String remark) throws SQLException {
        return change(c, userId, amount, "REFUND", remark);
    }

    /**
     * 余额变动核心：悲观锁住用户行 → 校验 → 更新余额 → 写流水。
     * 本方法不提交也不回滚，事务由调用方控制。
     *
     * @param delta 变动额：正数为收入，负数为支出
     * @throws IllegalArgumentException 余额不足
     */
    static BalanceLog change(Connection c, long userId, BigDecimal delta, String type, String remark)
            throws SQLException {
        BigDecimal current = BalanceDao.lockBalance(c, userId);      // SELECT ... FOR UPDATE，其它并发在此阻塞
        BigDecimal after = current.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("余额不足，当前余额 "
                    + current.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + "，还差 " + after.negate().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        BalanceDao.updateBalance(c, userId, after);

        BalanceLog log = new BalanceLog();
        log.userId = userId;
        log.changeAmount = delta.setScale(2, RoundingMode.HALF_UP).doubleValue();
        log.balanceAfter = after.doubleValue();
        log.type = type;
        log.remark = remark;
        BalanceDao.insertLog(c, log);
        return log;
    }

    /**
     * 懒初始化：账号首次访问余额时补一条「初始赠金」流水。
     * 幂等——持锁后再查一次，避免并发下重复插入。
     */
    public static void ensureInitialized(long userId) throws SQLException {
        if (BalanceDao.hasLog(userId)) return;
        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                BigDecimal current = BalanceDao.lockBalance(c, userId);
                if (!BalanceDao.hasLog(c, userId)) {
                    BalanceLog log = new BalanceLog();
                    log.userId = userId;
                    log.changeAmount = current.doubleValue();
                    log.balanceAfter = current.doubleValue();
                    log.type = "INIT";
                    log.remark = "注册赠送金币";
                    BalanceDao.insertLog(c, log);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** 金额规整：保留 2 位小数，四舍五入。 */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static double money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
