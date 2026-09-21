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
import org.example.restaurant.model.Order;
import org.example.restaurant.model.User;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 订单接口：
 * GET    /api/orders           我的订单
 * POST   /api/orders           提交订单（事务扣库存）
 * DELETE /api/orders?id=1      取消订单（回滚库存）
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
        String payType = WebUtil.str(p, "payType", "CASH");
        String remark = WebUtil.str(p, "remark").trim();
        Cart cart = WebUtil.cart(req);
        if (cart.isEmpty()) {
            WebUtil.json(resp, 400, Resp.fail(400, "购物车为空，请先点菜"));
            return;
        }
        try {
            Order order = OrderDao.create(user.id, new LinkedHashMap<>(cart.getRaw()), payType, remark);
            cart.clear();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("order", order);
            data.put("cartCount", cart.size());
            WebUtil.json(resp, Resp.ok(data));
            System.out.println("[下单] 用户 " + user.username + " 创建订单 " + order.orderNo
                    + "，金额 " + order.totalAmount);
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
            boolean ok = OrderDao.cancel(user.id, id);
            if (!ok) {
                WebUtil.json(resp, 400, Resp.fail(400, "该订单不存在或不可取消"));
                return;
            }
            WebUtil.json(resp, Resp.ok(Map.of("cancelled", id)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "取消订单失败：" + e.getMessage()));
        }
    }
}
