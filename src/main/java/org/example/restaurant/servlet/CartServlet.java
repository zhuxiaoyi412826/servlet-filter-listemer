package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.dao.DishDao;
import org.example.restaurant.model.Cart;
import org.example.restaurant.model.CartItem;
import org.example.restaurant.model.Dish;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 购物车（数据存于 Session，菜品价格实时读库，避免价格被篡改）：
 * GET    /api/cart                 查看
 * POST   /api/cart                 加入 / 覆盖数量
 * DELETE /api/cart?dishId=1        删除某一项（不带 dishId 表示清空）
 */
@WebServlet(name = "cartServlet", urlPatterns = "/api/cart")
public class CartServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            WebUtil.json(resp, Resp.ok(buildCartData(req)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "读取购物车失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        long dishId = WebUtil.longOf(p, "dishId", 0L);
        int quantity = WebUtil.intOf(p, "quantity", 1);
        boolean replace = "set".equals(WebUtil.str(p, "action"));
        if (dishId <= 0) {
            WebUtil.json(resp, 400, Resp.fail(400, "请选择要加入购物车的菜品"));
            return;
        }
        try {
            Dish dish = DishDao.findById(dishId);
            if (dish == null || dish.status == 0) {
                WebUtil.json(resp, 400, Resp.fail(400, "该菜品已下架，无法加入购物车"));
                return;
            }
            Cart cart = WebUtil.cart(req);
            if (replace) {
                cart.set(dishId, quantity);
            } else {
                if (dish.stock <= 0) {
                    WebUtil.json(resp, 400, Resp.fail(400, "【" + dish.name + "】已售罄"));
                    return;
                }
                int next = (int) (cart.getRaw().getOrDefault(dishId, 0) + quantity);
                if (next > dish.stock) {
                    WebUtil.json(resp, 400, Resp.fail(400, "【" + dish.name + "】库存仅剩 " + dish.stock + " 份"));
                    return;
                }
                cart.add(dishId, quantity);
            }
            Map<String, Object> data = buildCartData(req);
            data.put("addedDish", dish.name);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "操作购物车失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        long dishId = WebUtil.longOf(p, "dishId", 0L);
        Cart cart = WebUtil.cart(req);
        if (dishId > 0) cart.remove(dishId);
        else cart.clear();
        try {
            WebUtil.json(resp, Resp.ok(buildCartData(req)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "操作购物车失败：" + e.getMessage()));
        }
    }

    /** 组装购物车展示数据：实时价格 + 库存校验 + 失效剔除。 */
    private Map<String, Object> buildCartData(HttpServletRequest req) throws SQLException {
        Cart cart = WebUtil.cart(req);
        Map<Long, Integer> raw = cart.getRaw();
        List<Long> ids = new ArrayList<>(raw.keySet());
        Map<Long, Dish> dishes = DishDao.findMapByIds(ids);
        List<CartItem> items = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        double total = 0;
        int count = 0;
        for (Long id : ids) {
            Dish d = dishes.get(id);
            if (d == null || d.status == 0) {
                removed.add(id.toString());
                continue;
            }
            int q = Math.min(raw.get(id), Math.max(d.stock, 1));
            raw.put(id, q);
            CartItem ci = new CartItem();
            ci.dishId = d.id;
            ci.name = d.name;
            ci.imageUrl = d.imageUrl;
            ci.price = d.price;
            ci.quantity = q;
            ci.stock = d.stock;
            ci.amount = Cart.round(d.price * q);
            items.add(ci);
            total += ci.getAmount();
            count += q;
        }
        removed.forEach(r -> cart.remove(Long.valueOf(r)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("count", count);
        data.put("total", Cart.round(total));
        data.put("removed", removed);
        return data;
    }
}
