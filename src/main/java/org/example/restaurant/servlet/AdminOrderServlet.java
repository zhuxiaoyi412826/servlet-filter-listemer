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
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 后台订单管理（AdminFilter 保护）：
 * GET /api/admin/orders?orderNo=&status=&from=&to=&keyword=&page=&size=  分页列表 + 状态概览
 * PUT /api/admin/orders {id, status}  接单 / 标记完成 / 取消（自动回滚库存）
 */
@WebServlet(name = "adminOrderServlet", urlPatterns = "/api/admin/orders")
public class AdminOrderServlet extends HttpServlet {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Map<String, Object> p = WebUtil.params(req);
            WebUtil.json(resp, Resp.ok(pageData(p)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "查询订单失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        long id = WebUtil.longOf(p, "id", 0L);
        String status = WebUtil.str(p, "setStatus").trim();
        if (id <= 0 || status.isEmpty()) {
            WebUtil.json(resp, 400, Resp.fail(400, "缺少订单编号或目标状态"));
            return;
        }
        try {
            OrderDao.updateStatusAsAdmin(id, status);
            Map<String, Object> data = pageData(p);
            data.put("message", "订单状态已更新");
            WebUtil.json(resp, Resp.ok(data));
        } catch (IllegalArgumentException e) {
            WebUtil.json(resp, 400, Resp.fail(400, e.getMessage()));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "改单失败：" + e.getMessage()));
        }
    }

    private Map<String, Object> pageData(Map<String, Object> p) throws SQLException {
        String orderNo = WebUtil.str(p, "orderNo").trim();
        String status = WebUtil.str(p, "status").trim();
        String from = WebUtil.str(p, "from").trim();
        String to = WebUtil.str(p, "to").trim();
        String keyword = WebUtil.str(p, "keyword").trim();
        int page = Math.max(WebUtil.intOf(p, "page", 1), 1);
        int size = Math.min(Math.max(WebUtil.intOf(p, "size", DEFAULT_SIZE), 1), MAX_SIZE);
        int total = OrderDao.countForAdmin(orderNo, status, from, to, keyword);
        int pages = (int) Math.ceil(total * 1.0 / size);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("orderNo", orderNo);
        filters.put("status", status);
        filters.put("from", from);
        filters.put("to", to);
        filters.put("keyword", keyword);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", OrderDao.searchForAdmin(orderNo, status, from, to, keyword, size, (page - 1) * size));
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("pages", pages);
        data.put("filters", filters);
        data.put("overview", OrderDao.statusOverview());
        return data;
    }
}
