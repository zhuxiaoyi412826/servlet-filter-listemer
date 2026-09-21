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
import org.example.restaurant.dao.UserDao;
import org.example.restaurant.model.User;
import org.example.restaurant.util.PasswordUtil;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 后台用户管理（AdminFilter 保护）：
 * GET    /api/admin/user?keyword=&role=&status=&page=&size=  列表 + 分页 + 概览
 * PUT    /api/admin/user {id, role|status|reset}             改角色 / 启用禁用 / 重置密码
 * DELETE /api/admin/user?id=1                                删除用户
 * 写操作后回显最新列表，前端无需二次请求。
 */
@WebServlet(name = "adminUserServlet", urlPatterns = "/api/admin/user")
public class AdminUserServlet extends HttpServlet {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Map<String, Object> p = WebUtil.params(req);
            WebUtil.json(resp, Resp.ok(pageData(p)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "查询用户失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        long id = WebUtil.longOf(p, "id", 0L);
        if (id <= 0) {
            WebUtil.json(resp, 400, Resp.fail(400, "缺少用户编号"));
            return;
        }
        User me = WebUtil.currentUser(req);
        if (me != null && me.id != null && me.id == id) {
            WebUtil.json(resp, 400, Resp.fail(400, "不能修改当前登录账号的角色或状态"));
            return;
        }
        try {
            User target = UserDao.findById(id);
            if (target == null) {
                WebUtil.json(resp, 404, Resp.fail(404, "用户不存在"));
                return;
            }
            // 写操作字段名加 set 前缀，避免与列表过滤参数同名
            String role = WebUtil.str(p, "setRole").trim();
            String status = WebUtil.str(p, "setStatus").trim();
            boolean reset = WebUtil.boolOf(p, "reset", false);
            String msg;
            if (!role.isEmpty()) {
                boolean toAdmin = role.equalsIgnoreCase("ADMIN");
                if (!toAdmin && target.isAdmin() && UserDao.countAdmins() <= 1) {
                    WebUtil.json(resp, 400, Resp.fail(400, "系统至少需要保留一名管理员"));
                    return;
                }
                UserDao.updateRole(id, role);
                msg = toAdmin ? "已设为管理员" : "已设为普通用户";
            } else if (!status.isEmpty()) {
                int st = status.equals("0") || status.equalsIgnoreCase("false") ? 0 : 1;
                UserDao.updateStatus(id, st);
                msg = st == 1 ? "账号已启用" : "账号已禁用";
            } else if (reset) {
                String newPwd = PasswordUtil.randomDigits(6);
                String salt = PasswordUtil.salt();
                UserDao.resetPassword(id, PasswordUtil.hash(newPwd, salt), salt);
                msg = "密码已重置为 " + newPwd + "，请及时告知用户";
            } else {
                WebUtil.json(resp, 400, Resp.fail(400, "缺少要修改的字段"));
                return;
            }
            Map<String, Object> data = pageData(p);
            data.put("message", msg);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "操作失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        long id = WebUtil.longOf(p, "id", 0L);
        if (id <= 0) {
            WebUtil.json(resp, 400, Resp.fail(400, "缺少用户编号"));
            return;
        }
        User me = WebUtil.currentUser(req);
        if (me != null && me.id != null && me.id == id) {
            WebUtil.json(resp, 400, Resp.fail(400, "不能删除当前登录账号"));
            return;
        }
        try {
            User target = UserDao.findById(id);
            if (target == null) {
                WebUtil.json(resp, 404, Resp.fail(404, "用户不存在"));
                return;
            }
            if (target.isAdmin() && UserDao.countAdmins() <= 1) {
                WebUtil.json(resp, 400, Resp.fail(400, "系统至少需要保留一名管理员"));
                return;
            }
            UserDao.deleteUser(id);
            Map<String, Object> data = pageData(p);
            data.put("message", "用户已删除");
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "删除失败：" + e.getMessage()));
        }
    }

    /** 组装「列表 + 分页 + 概览」，写操作后复用，前端直接回显。 */
    private Map<String, Object> pageData(Map<String, Object> p) throws SQLException {
        String keyword = WebUtil.str(p, "keyword").trim();
        String role = WebUtil.str(p, "role").trim();
        String statusRaw = WebUtil.str(p, "status").trim();
        Integer status = statusRaw.isEmpty() ? null : (statusRaw.equals("0") ? 0 : 1);
        int page = Math.max(WebUtil.intOf(p, "page", 1), 1);
        int size = Math.min(Math.max(WebUtil.intOf(p, "size", DEFAULT_SIZE), 1), MAX_SIZE);
        int total = UserDao.countForAdmin(keyword, role, status);
        int pages = (int) Math.ceil(total * 1.0 / size);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", keyword);
        filters.put("role", role);
        filters.put("status", statusRaw.isEmpty() ? "" : String.valueOf(status));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", UserDao.searchForAdmin(keyword, role, status, size, (page - 1) * size));
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("pages", pages);
        data.put("filters", filters);
        data.put("stats", UserDao.stats());
        return data;
    }
}
