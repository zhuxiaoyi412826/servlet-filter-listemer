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
import org.example.restaurant.util.PasswordUtil;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 用户注册：服务端完整校验，失败时回传 errors + 已填表单数据，
 * 前端据此做错误提示并回显用户输入（不丢数据）。
 */
@WebServlet(name = "registerServlet", urlPatterns = "/api/auth/register")
public class RegisterServlet extends HttpServlet {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        String username = WebUtil.str(p, "username").trim();
        String password = WebUtil.str(p, "password");
        String confirm = WebUtil.str(p, "confirm");
        String phone = WebUtil.str(p, "phone").trim();
        String nickname = WebUtil.str(p, "nickname").trim();
        String address = WebUtil.str(p, "address").trim();

        Map<String, String> errors = new LinkedHashMap<>();
        if (username.isEmpty()) errors.put("username", "用户名不能为空");
        else if (!USERNAME.matcher(username).matches()) errors.put("username", "3-20 位字母、数字或下划线");

        if (password.isEmpty()) errors.put("password", "密码不能为空");
        else if (password.length() < 6 || password.length() > 20) errors.put("password", "密码长度需在 6-20 位之间");

        if (!password.equals(confirm)) errors.put("confirm", "两次输入的密码不一致");

        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) errors.put("phone", "手机号格式不正确（选填）");

        if (nickname.isEmpty()) nickname = username;
        if (nickname.length() > 20) errors.put("nickname", "昵称不能超过 20 个字符");

        try {
            if (!errors.isEmpty()) {
                WebUtil.json(resp, 400, Resp.fail(400, "请修正表单中的错误", echo(username, phone, nickname, address, errors)));
                return;
            }
            if (UserDao.exists("username", username)) {
                errors.put("username", "该用户名已被注册");
            }
            if (!phone.isEmpty() && UserDao.exists("phone", phone)) {
                errors.put("phone", "该手机号已被绑定");
            }
            if (!errors.isEmpty()) {
                WebUtil.json(resp, 400, Resp.fail(400, "注册信息校验未通过", echo(username, phone, nickname, address, errors)));
                return;
            }
            String salt = PasswordUtil.salt();
            long id = UserDao.insert(username, PasswordUtil.hash(password, salt), salt, nickname,
                    phone.isEmpty() ? null : phone, "", address);
            User user = UserDao.findById(id);
            // 注册成功自动登录
            req.getSession(true).setAttribute(WebUtil.SESSION_USER, user);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user);
            data.put("autoLogin", true);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "注册失败：" + e.getMessage(),
                    echo(username, phone, nickname, address, errors)));
        }
    }

    private Map<String, Object> echo(String username, String phone, String nickname, String address,
                                     Map<String, String> errors) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("username", username);
        form.put("phone", phone);
        form.put("nickname", nickname);
        form.put("address", address);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("form", form);
        data.put("errors", errors);
        return data;
    }
}
