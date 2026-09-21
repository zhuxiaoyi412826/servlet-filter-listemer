package org.example.restaurant.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.model.User;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/** 管理员权限过滤器：仅 ADMIN 角色可访问 /api/admin/*。 */
public class AdminFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        User user = WebUtil.currentUser(req);
        if (user == null) {
            WebUtil.json(resp, 401, Resp.fail(401, "请先登录"));
            return;
        }
        if (!user.isAdmin()) {
            WebUtil.json(resp, 403, Resp.fail(403, "需要管理员权限才能访问该资源"));
            return;
        }
        chain.doFilter(request, response);
    }
}
