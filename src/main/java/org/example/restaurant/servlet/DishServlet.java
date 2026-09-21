package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.dao.DishDao;
import org.example.restaurant.model.Dish;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 菜品接口：
 * GET /api/dishes                 列表（支持 category / keyword 过滤）
 * GET /api/dishes?id=1            详情，同时写入浏览历史 Cookie（回显"最近浏览"）
 */
@WebServlet(name = "dishServlet", urlPatterns = "/api/dishes")
public class DishServlet extends HttpServlet {

    private static final int HISTORY_MAX = 8;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        String category = WebUtil.str(p, "category");
        String keyword = WebUtil.str(p, "keyword");
        long id = WebUtil.longOf(p, "id", 0L);
        try {
            if (id > 0) {
                Dish dish = DishDao.findById(id);
                if (dish == null) {
                    WebUtil.json(resp, 404, Resp.fail(404, "菜品不存在或已下架"));
                    return;
                }
                recordHistory(req, resp, String.valueOf(id));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("dish", dish);
                data.put("history", historyList(req));
                WebUtil.json(resp, Resp.ok(data));
                return;
            }
            List<Dish> list = DishDao.search(category, keyword, true);
            List<String> categories = DishDao.categories();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", list);
            data.put("categories", categories);
            data.put("total", list.size());
            data.put("category", category);
            data.put("keyword", keyword);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "查询菜品失败：" + e.getMessage()));
        }
    }

    /** 浏览历史写入 Cookie（最多保留 HISTORY_MAX 条，最新在前）。 */
    private void recordHistory(HttpServletRequest req, HttpServletResponse resp, String dishId) {
        List<String> ids = new ArrayList<>(historyList(req));
        ids.remove(dishId);
        ids.add(0, dishId);
        if (ids.size() > HISTORY_MAX) ids = ids.subList(0, HISTORY_MAX);
        WebUtil.setCookie(resp, WebUtil.COOKIE_HISTORY, String.join(",", ids), 7 * 24 * 3600);
    }

    private List<String> historyList(HttpServletRequest req) {
        String value = WebUtil.cookie(req, WebUtil.COOKIE_HISTORY);
        Set<String> set = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            for (String s : value.split(",")) {
                if (!s.isBlank()) set.add(s.trim());
            }
        }
        return new ArrayList<>(set);
    }
}
