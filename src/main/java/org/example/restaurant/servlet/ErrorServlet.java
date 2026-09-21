package org.example.restaurant.servlet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 统一异常处理出口（web.xml error-page 指向此处）。
 * Ajax 请求返回 JSON，普通请求返回错误页提示。
 */
@WebServlet(name = "errorServlet", urlPatterns = "/api/error")
public class ErrorServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer status = (Integer) req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Throwable ex = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String uri = String.valueOf(req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        String message = ex == null ? (String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE) : ex.getMessage();
        int code = status == null ? 500 : status;
        System.err.println("[错误] " + code + " " + uri + " " + message);
        boolean ajax = "XMLHttpRequest".equals(req.getHeader("X-Requested-With"))
                || (req.getHeader("Accept") != null && req.getHeader("Accept").contains("application/json"));
        if (ajax) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uri", uri);
            data.put("error", message);
            WebUtil.json(resp, code == 0 ? 500 : code, Resp.fail(code == 0 ? 500 : code, "服务器内部错误", data));
            return;
        }
        req.setAttribute("errorCode", code);
        req.setAttribute("errorMessage", message == null ? "未知错误" : message);
        req.getRequestDispatcher("/static/404.html").forward(req, resp);
    }
}
