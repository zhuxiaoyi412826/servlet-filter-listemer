package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.dao.OrderDao;
import org.example.restaurant.dao.UserDao;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 后台收入管理（AdminFilter 保护）：
 * GET /api/admin/income?days=7&top=8
 * 返回收入概览、每日趋势（自动补零）、菜品销量榜、支付方式分布、用户概览。
 */
@WebServlet(name = "adminIncomeServlet", urlPatterns = "/api/admin/income")
public class AdminIncomeServlet extends HttpServlet {

    private static final int MAX_DAYS = 90;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        int days = Math.min(Math.max(WebUtil.intOf(p, "days", 7), 1), MAX_DAYS);
        int top = Math.min(Math.max(WebUtil.intOf(p, "top", 8), 1), 20);
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("days", days);
            data.put("overview", OrderDao.incomeOverview());
            data.put("daily", fillZero(OrderDao.dailyIncome(days), days));
            data.put("dishes", OrderDao.dishRanking(top));
            data.put("payTypes", OrderDao.payTypeStats());
            data.put("users", UserDao.stats());
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "统计失败：" + e.getMessage()));
        }
    }

    /** 按日期补齐缺失的天数（收入计 0），便于前端直接绘制趋势图。 */
    private List<Map<String, Object>> fillZero(List<Map<String, Object>> rows, int days) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            index.put(String.valueOf(r.get("day")), r);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            String key = start.plusDays(i).toString();
            Map<String, Object> exist = index.get(key);
            if (exist != null) {
                out.add(exist);
            } else {
                Map<String, Object> zero = new LinkedHashMap<>();
                zero.put("day", key);
                zero.put("orders", 0L);
                zero.put("revenue", 0D);
                out.add(zero);
            }
        }
        return out;
    }
}
