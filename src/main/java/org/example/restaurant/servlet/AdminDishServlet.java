package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.dao.DishDao;
import org.example.restaurant.model.Dish;
import org.example.restaurant.util.MinioStore;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 后台菜品管理（需 ADMIN 角色，由 AdminFilter 拦截）：
 * POST   /api/admin/dish           新增
 * PUT    /api/admin/dish           修改
 * DELETE /api/admin/dish?id=1      删除
 * 均返回最新菜品列表，便于前端直接回显。
 */
@WebServlet(name = "adminDishServlet", urlPatterns = "/api/admin/dish")
@MultipartConfig(maxFileSize = 1024 * 1024 * 5)
public class AdminDishServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Map<String, Object> p = WebUtil.params(req);
            Dish d = read(p, true);
            Map<String, String> errors = validate(p, d);
            if (!errors.isEmpty()) {
                echoForm(resp, 400, "请修正表单中的错误", p, errors);
                return;
            }
            String image = uploadImage(req, "file");
            if (image != null) d.imageUrl = image;
            DishDao.insert(d);
            list(resp, "新增菜品成功");
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "新增失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Map<String, Object> p = WebUtil.params(req);
            long id = WebUtil.longOf(p, "id", 0L);
            Dish exist = DishDao.findById(id);
            if (exist == null) {
                WebUtil.json(resp, 404, Resp.fail(404, "菜品不存在"));
                return;
            }
            Dish d = read(p, false);
            d.id = id;
            Map<String, String> errors = validate(p, d);
            if (!errors.isEmpty()) {
                echoForm(resp, 400, "请修正表单中的错误", p, errors);
                return;
            }
            String image = uploadImage(req, "file");
            d.imageUrl = image != null ? image : exist.imageUrl;
            DishDao.update(d);
            list(resp, "修改成功");
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "修改失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            long id = WebUtil.longOf(WebUtil.params(req), "id", 0L);
            if (id <= 0) {
                WebUtil.json(resp, 400, Resp.fail(400, "缺少菜品编号"));
                return;
            }
            DishDao.delete(id);
            list(resp, "删除成功");
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "删除失败：" + e.getMessage()));
        }
    }

    private Dish read(Map<String, Object> p, boolean create) {
        Dish d = new Dish();
        d.id = WebUtil.longOf(p, "id", 0L);
        d.name = WebUtil.str(p, "name").trim();
        d.category = WebUtil.str(p, "category").trim();
        d.price = WebUtil.doubleOf(p, "price", 0D);
        d.stock = WebUtil.intOf(p, "stock", 0);
        d.description = WebUtil.str(p, "description").trim();
        d.imageUrl = WebUtil.str(p, "imageUrl").trim();
        d.status = WebUtil.intOf(p, "status", create ? 1 : 0);
        return d;
    }

    private Map<String, String> validate(Map<String, Object> p, Dish d) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (d.name.isEmpty()) errors.put("name", "菜名不能为空");
        else if (d.name.length() > 50) errors.put("name", "菜名不能超过 50 字");
        if (d.category.isEmpty()) errors.put("category", "请选择分类");
        if (d.price <= 0) errors.put("price", "价格必须大于 0");
        else if (d.price > 99999) errors.put("price", "价格超出合理范围");
        if (d.stock < 0) errors.put("stock", "库存不能为负数");
        if (d.description.length() > 200) errors.put("description", "描述不能超过 200 字");
        return errors;
    }

    private void echoForm(HttpServletResponse resp, int status, String msg,
                          Map<String, Object> p, Map<String, String> errors) throws IOException {
        Map<String, Object> form = new LinkedHashMap<>();
        for (String key : new String[]{"id", "name", "category", "price", "stock", "description", "imageUrl", "status"}) {
            form.put(key, p.get(key));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("form", form);
        data.put("errors", errors);
        WebUtil.json(resp, status, Resp.fail(status, msg, data));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            list(resp, null);
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "查询失败：" + e.getMessage()));
        }
    }

    private void list(HttpServletResponse resp, String msg) throws SQLException, IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", DishDao.findAll());
        data.put("categories", DishDao.categories());
        if (msg != null) data.put("message", msg);
        WebUtil.json(resp, Resp.ok(data));
    }

    /** 可选的图片上传（未传 file 返回 null）。 */
    private String uploadImage(HttpServletRequest req, String field) {
        try {
            var part = req.getPart(field);
            if (part == null || part.getSize() == 0) return null;
            String ct = part.getContentType();
            if (ct == null || !ct.startsWith("image/")) return null;
            byte[] bytes = part.getInputStream().readAllBytes();
            String ext = ct.contains("png") ? "png" : (ct.contains("gif") ? "gif" : "jpg");
            return MinioStore.upload(bytes, ct, ext, "dish");
        } catch (Exception e) {
            return null;
        }
    }
}
