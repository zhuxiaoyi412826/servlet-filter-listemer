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
import org.example.restaurant.util.PasswordUtil;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 短信验证码下发（演示环境未接入短信网关，验证码同时返回并在控制台打印，
 * 便于本地调试；生产替换 AuthDao.savePhoneCode 前后的短信发送逻辑即可）。
 */
@WebServlet(name = "phoneCodeServlet", urlPatterns = "/api/auth/phone/code")
public class PhoneCodeServlet extends HttpServlet {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int EXPIRE_MINUTES = 5;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> p = WebUtil.params(req);
        String phone = WebUtil.str(p, "phone").trim();
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("phone", phone);
        if (!PHONE.matcher(phone).matches()) {
            WebUtil.json(resp, 400, Resp.fail(400, "请输入正确的 11 位手机号", echo));
            return;
        }
        String code = PasswordUtil.randomDigits(6);
        try {
            AuthDao.savePhoneCode(phone, code, EXPIRE_MINUTES);
            System.out.println("[短信] 手机号 " + phone + " 的验证码为：" + code + "（" + EXPIRE_MINUTES + " 分钟内有效）");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phone", phone);
            data.put("devCode", code);       // 演示用：前端辅助回显，生产环境请删除
            data.put("expireMinutes", EXPIRE_MINUTES);
            WebUtil.json(resp, Resp.ok(data));
        } catch (SQLException e) {
            WebUtil.json(resp, 500, Resp.fail(500, "验证码发送失败：" + e.getMessage(), echo));
        }
    }
}
