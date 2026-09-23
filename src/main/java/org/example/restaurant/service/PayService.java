package org.example.restaurant.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.restaurant.dao.OrderDao;
import org.example.restaurant.model.BalanceLog;
import org.example.restaurant.model.Order;
import org.example.restaurant.util.DbUtil;

/**
 * 支付业务层：把「下单」与「扣余额」编排进同一个事务。
 * <p>
 * 单个事务内的原子操作：
 * <pre>
 *   下单：写订单 + 写明细 + 扣库存 + 扣余额 + 写流水
 *   取消：改状态 + 还库存 + 退余额 + 写流水
 * </pre>
 * 任一步失败整体回滚，不会出现「订单生成了但没扣钱」或「扣了钱没订单」。
 */
public final class PayService {

    private PayService() {
    }

    /**
     * 结算购物车：创建订单并自动从余额扣款（同一事务 + 悲观锁）。
     *
     * @return {order, cartCount, paid, balance}
     * @throws IllegalArgumentException 购物车为空、库存不足、余额不足
     */
    public static Map<String, Object> checkout(long userId, Map<Long, Integer> cartItems,
                                               String payType, String remark) throws SQLException {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("购物车是空的，请先点菜");
        }
        // 首次使用时补齐「初始赠金」流水，保证流水从 1000 起步、每笔变动都可追溯
        BalanceService.ensureInitialized(userId);
        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                // 1. 下单：写订单 + 明细 + 扣库存（事务版本，不自己提交）
                Order order = OrderDao.insertInTx(c, userId, cartItems, payType, remark);
                // 2. 扣余额：悲观锁住用户行，写 SPEND 流水
                BalanceLog log = BalanceService.payInTx(c, userId,
                        BigDecimal.valueOf(order.totalAmount), "订单支付 " + order.orderNo);
                c.commit();

                System.out.printf("[支付] 用户 %d 支付订单 %s，扣款 %s，余额 %s%n",
                        userId, order.orderNo, order.totalAmount, log.balanceAfter);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("order", order);
                data.put("paid", -log.changeAmount);
                data.put("balance", log.balanceAfter);
                return data;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * 取消订单：改状态 + 归还库存 + 原路退回余额（同一事务）。
     *
     * @return {refund, balance, orderNo}
     * @throws IllegalArgumentException 订单不存在、状态不允许取消、重复退款
     */
    public static Map<String, Object> cancelWithRefund(long userId, long orderId) throws SQLException {
        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> info = OrderDao.lockOrder(c, userId, orderId);   // 悲观锁订单行
                if (info == null) throw new IllegalArgumentException("订单不存在");
                if (!"CREATED".equals(info.get("status"))) {
                    throw new IllegalArgumentException("该订单当前状态不可取消");
                }
                BigDecimal refund = BigDecimal.valueOf((Double) info.get("totalAmount"));
                String orderNo = String.valueOf(info.get("orderNo"));

                boolean ok = OrderDao.cancelInTx(c, userId, orderId);   // 状态改 CANCELLED + 还库存
                if (!ok) throw new IllegalArgumentException("订单不可取消");

                BalanceLog log = BalanceService.refundInTx(c, userId, refund, "订单退款 " + orderNo);
                c.commit();

                System.out.printf("[支付] 用户 %d 取消订单 %s，退款 %s，余额 %s%n",
                        userId, orderNo, refund.toPlainString(), log.balanceAfter);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("orderNo", orderNo);
                data.put("refund", refund.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
                data.put("balance", log.balanceAfter);
                return data;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
