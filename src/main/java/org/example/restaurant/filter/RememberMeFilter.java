package org.example.restaurant.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.restaurant.dao.AuthDao;
import org.example.restaurant.model.User;
import org.example.restaurant.util.WebUtil;

/**
 * 记住我：Session 中没有登录用户、但浏览器带有 rm_token Cookie 时，
 * 自动查询令牌表重建 Session（实现"关闭浏览器再打开仍然已登录"）。
 */
public class RememberMeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = req.getSession(false);
        Object exist = session == null ? null : session.getAttribute(WebUtil.SESSION_USER);
        if (exist == null) {
            String token = WebUtil.cookie(req, WebUtil.COOKIE_REMEMBER);
            if (token != null && !token.isBlank()) {
                try {
                    User user = AuthDao.findUserByToken(token);
                    if (user != null && user.isDisabled()) {
                        System.out.println("[RememberMe] 账号已禁用，跳过自动登录: " + user.username);
                        WebUtil.removeCookie(resp, WebUtil.COOKIE_REMEMBER);
                    } else if (user != null) {
                        req.getSession(true).setAttribute(WebUtil.SESSION_USER, user);
                        System.out.println("[RememberMe] Cookie 自动登录成功: " + user.username);
                    } else {
                        // 过期或无效令牌，清理 Cookie
                        WebUtil.removeCookie(resp, WebUtil.COOKIE_REMEMBER);
                    }
                } catch (Exception e) {
                    System.err.println("[RememberMe] 自动登录失败: " + e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
