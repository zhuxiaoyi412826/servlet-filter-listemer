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
import org.example.restaurant.dao.AuthDao;
import org.example.restaurant.dao.UserDao;
import org.example.restaurant.model.User;
import org.example.restaurant.util.PasswordUtil;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 手机号 + 验证码登录：
 * 未注册的手机号自动建号（用户名即为手机号），实现"短信验证码即注册即登录"。
 */
@WebServlet(name = "phoneLoginServlet", urlPatterns = "/api/auth/phone/login")
public class PhoneLoginServlet extends HttpServlet {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int REMEMBER_DAYS = 30;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        String phone = WebUtil.str(p, "phone").trim();
        String code = WebUtil.str(p, "code").trim();
        boolean remember = WebUtil.boolOf(p, "rememberMe", false);

        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("phone", phone);
        echo.put("rememberMe", remember);

        if (!PHONE.matcher(phone).matches()) {
            WebUtil.json(resp, 400, Resp.fail(400, "请输入正确的 11 位手机号", echo));
            return;
        }
        if (code.length() != 6) {
            WebUtil.json(resp, 400, Resp.fail(400, "验证码为 6 位数字", echo));
            return;
        }
        try {
            boolean ok = AuthDao.verifyPhoneCode(phone, code);
            if (!ok) {
                WebUtil.json(resp, 400, Resp.fail(400, "验证码错误或已过期", echo));
                return;
            }
            User user = UserDao.findByPhone(phone);
            boolean isNew = false;
            if (user == null) {
                String salt = PasswordUtil.salt();
                long id = UserDao.insert(phone, PasswordUtil.hash(PasswordUtil.randomToken(), salt), salt,
                        "用户" + phone.substring(7), phone, "", "");
                user = UserDao.findById(id);
                isNew = true;
                System.out.println("[登录] 手机号首次登录自动注册: " + phone);
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
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user);
            data.put("isNewUser", isNew);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "登录失败：" + e.getMessage(), echo));
        }
    }
}
