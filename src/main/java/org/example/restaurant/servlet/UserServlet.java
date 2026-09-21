package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.dao.UserDao;
import org.example.restaurant.model.User;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 个人资料维护：
 * GET  /api/user/profile   回显当前资料
 * POST /api/user/profile   保存（昵称 / 手机号 / 地址 / 头像地址）
 */
@WebServlet(name = "userServlet", urlPatterns = "/api/user/profile")
public class UserServlet extends HttpServlet {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        try {
            User fresh = UserDao.findById(user.id);
            req.getSession(true).setAttribute(WebUtil.SESSION_USER, fresh);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", fresh);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "读取资料失败：" + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User current = WebUtil.currentUser(req);
        Map<String, Object> p = WebUtil.params(req);
        String nickname = WebUtil.str(p, "nickname").trim();
        String phone = WebUtil.str(p, "phone").trim();
        String address = WebUtil.str(p, "address").trim();
        String avatar = WebUtil.str(p, "avatar").trim();

        Map<String, String> errors = new LinkedHashMap<>();
        if (nickname.isEmpty()) errors.put("nickname", "昵称不能为空");
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) errors.put("phone", "手机号格式不正确");
        if (avatar.isEmpty()) avatar = current.avatar;

        User updated = new User();
        updated.id = current.id;
        updated.nickname = nickname;
        updated.phone = phone;
        updated.address = address;
        updated.avatar = avatar;

        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("form", updated);
        echo.put("errors", errors);
        if (!errors.isEmpty()) {
            WebUtil.json(resp, 400, Resp.fail(400, "请修正表单中的错误", echo));
            return;
        }
        try {
            // 手机号重复校验（排除自己）
            if (!phone.isEmpty()) {
                User same = UserDao.findByPhone(phone);
                if (same != null && same.id != null && same.id.longValue() != current.id.longValue()) {
                    errors.put("phone", "该手机号已被其他账号绑定");
                    WebUtil.json(resp, 400, Resp.fail(400, "手机号已被占用", echo));
                    return;
                }
            }
            UserDao.updateProfile(updated);
            User fresh = UserDao.findById(current.id);
            User sessionUser = new User();
            sessionUser.id = fresh.id;
            sessionUser.username = fresh.username;
            sessionUser.nickname = fresh.nickname;
            sessionUser.phone = fresh.phone;
            sessionUser.avatar = fresh.avatar;
            sessionUser.address = fresh.address;
            sessionUser.role = fresh.role;
            sessionUser.createdAt = fresh.createdAt;
            req.getSession(true).setAttribute(WebUtil.SESSION_USER, sessionUser);
            WebUtil.json(resp, Resp.ok(Map.of("user", sessionUser)));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "保存失败：" + e.getMessage(), echo));
        }
    }
}
