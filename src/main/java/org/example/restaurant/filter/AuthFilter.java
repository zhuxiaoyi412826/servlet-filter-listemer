package org.example.restaurant.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 登录拦截过滤器：受保护接口未登录时返回 401 JSON，
 * 前端据此跳转登录页并回跳原地址（returnUrl）。
 */
public class AuthFilter implements Filter {

    private final Set<String> excluded = new LinkedHashSet<>();

    @Override
    public void init(FilterConfig config) {
        String param = config.getInitParameter("excluded");
        if (param != null && !param.isBlank()) {
            Arrays.stream(param.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(excluded::add);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        // getRequestURI() 含上下文路径（如 /servlet_filter_listemer_war），
        // 必须剥掉后再和白名单做前缀比较，否则非根路径部署时全部接口都会被判为需登录。
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        for (String prefix : excluded) {
            if (uri.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }
        if (WebUtil.currentUser(req) == null) {
            WebUtil.json(resp, 401, Resp.fail(401, "请先登录后再操作", (Object) java.util.Map.of("returnUrl", uri)));
            return;
        }
        chain.doFilter(request, response);
    }
}
