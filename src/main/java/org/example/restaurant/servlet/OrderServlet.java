package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.dao.OrderDao;
import org.example.restaurant.model.Cart;
import org.example.restaurant.model.User;
import org.example.restaurant.service.PayService;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 订单接口：
 * GET    /api/orders           我的订单
 * POST   /api/orders           提交订单（事务：扣库存 + 自动扣余额），业务由 PayService 编排
 * DELETE /api/orders?id=1      取消订单（事务：回滚库存 + 原路退款）
 */
@WebServlet(name = "orderServlet", urlPatterns = "/api/orders")
public class OrderServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        try {
            WebUtil.json(resp, Resp.ok(OrderDao.findByUser(user.id)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "查询订单失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        Map<String, Object> p = WebUtil.params(req);
        String remark = WebUtil.str(p, "remark").trim();
        Cart cart = WebUtil.cart(req);
        if (cart.isEmpty()) {
            WebUtil.json(resp, 400, Resp.fail(400, "购物车为空，请先点菜"));
            return;
        }
        try {
            // 结算：下单 + 自动扣余额（PayService 内部同一事务）
            Map<String, Object> data = PayService.checkout(user.id, new LinkedHashMap<>(cart.getRaw()),
                    "BALANCE", remark);
            cart.clear();
            data.put("cartCount", cart.size());
            WebUtil.json(resp, Resp.ok(data));
        } catch (IllegalArgumentException e) {
            WebUtil.json(resp, 400, Resp.fail(400, e.getMessage()));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "下单失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        long id = WebUtil.longOf(WebUtil.params(req), "id", 0L);
        if (id <= 0) {
            WebUtil.json(resp, 400, Resp.fail(400, "缺少订单编号"));
            return;
        }
        try {
            // 取消订单：库存返还 + 余额原路退回（同一事务）
            WebUtil.json(resp, Resp.ok(PayService.cancelWithRefund(user.id, id)));
        } catch (IllegalArgumentException e) {
            WebUtil.json(resp, 400, Resp.fail(400, e.getMessage()));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "取消订单失败：" + e.getMessage()));
        }
    }
}
