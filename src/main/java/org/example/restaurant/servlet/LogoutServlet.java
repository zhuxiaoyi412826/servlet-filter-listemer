package org.example.restaurant.servlet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.restaurant.dao.AuthDao;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/** 退出登录：销毁 Session 并清除"记住我"Cookie 与令牌表记录。 */
@WebServlet(name = "logoutServlet", urlPatterns = "/api/auth/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doLogout(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doLogout(req, resp);
    }

    private void doLogout(HttpServletRequest req, HttpServletResponse resp) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            String token = WebUtil.cookie(req, WebUtil.COOKIE_REMEMBER);
            if (token != null) {
                try {
                    AuthDao.removeToken(token);
                } catch (Exception ignored) {
                }
            }
            session.removeAttribute(WebUtil.SESSION_USER);
            session.invalidate();
        }
        WebUtil.removeCookie(resp, WebUtil.COOKIE_REMEMBER);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logout", true);
        WebUtil.json(resp, Resp.ok(data));
    }
}
