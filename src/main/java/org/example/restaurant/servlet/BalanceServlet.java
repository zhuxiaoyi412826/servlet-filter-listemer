package org.example.restaurant.servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.model.User;
import org.example.restaurant.service.BalanceService;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 金币余额接口（表现层：只做参数读取与 JSON 响应，业务全部交给 BalanceService）。
 * <p>
 * GET  /api/balance?page=1&size=10   余额概览 + 流水分页
 * POST /api/balance   {"amount":12.5,"remark":"自定义金额充值"}   充值（事务 + 悲观锁）
 * <p>
 * 消费扣款不再由这里发起——下单时由 PayService 在同一事务内自动扣减。
 */
@WebServlet(name = "balanceServlet", urlPatterns = "/api/balance")
public class BalanceServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        java.util.Map<String, Object> p = WebUtil.params(req);
        int page = WebUtil.intOf(p, "page", 1);
        int size = WebUtil.intOf(p, "size", 10);
        try {
            WebUtil.json(resp, Resp.ok(BalanceService.overview(user.id, page, size)));
        } catch (IllegalArgumentException e) {
            WebUtil.json(resp, 400, Resp.fail(400, e.getMessage()));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "查询余额失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        java.util.Map<String, Object> p = WebUtil.params(req);
        String amountText = WebUtil.str(p, "amount").trim();
        String remark = WebUtil.str(p, "remark").trim();

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException e) {
            WebUtil.json(resp, 400, Resp.fail(400, "金额格式不正确"));
            return;
        }
        try {
            WebUtil.json(resp, Resp.ok(BalanceService.recharge(user.id, amount, remark)));
        } catch (IllegalArgumentException e) {
            WebUtil.json(resp, 400, Resp.fail(400, e.getMessage()));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "充值失败：" + e.getMessage()));
        }
    }
}
