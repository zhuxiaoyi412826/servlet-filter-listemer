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
import org.example.restaurant.dao.AuthDao;
import org.example.restaurant.dao.UserDao;
import org.example.restaurant.model.User;
import org.example.restaurant.util.PasswordUtil;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 账号密码登录（用户名或手机号均可）。
 * 勾选"记住我"时下发 rm_token Cookie（30 天），用于关闭浏览器后自动登录。
 */
@WebServlet(name = "loginServlet", urlPatterns = "/api/auth/login")
public class LoginServlet extends HttpServlet {

    private static final int REMEMBER_DAYS = 30;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        String account = WebUtil.str(p, "account").trim();
        String password = WebUtil.str(p, "password");
        boolean remember = WebUtil.boolOf(p, "rememberMe", false);

        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("account", account);
        echo.put("rememberMe", remember);

        if (account.isEmpty() || password.isEmpty()) {
            WebUtil.json(resp, 400, Resp.fail(400, "账号和密码不能为空", echo));
            return;
        }
        try {
            User user = UserDao.findByAccount(account);
            if (user == null || !PasswordUtil.matches(password, user.salt, user.passwordHash)) {
                WebUtil.json(resp, 401, Resp.fail(401, "账号或密码错误", echo));
                return;
            }
            if (user.isDisabled()) {
                WebUtil.json(resp, 403, Resp.fail(403, "账号已被禁用，请联系管理员", echo));
                return;
            }
            req.getSession(true).setAttribute(WebUtil.SESSION_USER, user);
            if (remember) {
                String token = PasswordUtil.randomToken();
                AuthDao.saveToken(token, user.id, REMEMBER_DAYS);
                WebUtil.setCookie(resp, WebUtil.COOKIE_REMEMBER, token, REMEMBER_DAYS * 24 * 3600);
            } else {
                WebUtil.removeCookie(resp, WebUtil.COOKIE_REMEMBER);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user);
            data.put("rememberMe", remember);
            data.put("sessionId", req.getSession(false).getId());
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "登录失败：" + e.getMessage(), echo));
        }
    }
}
