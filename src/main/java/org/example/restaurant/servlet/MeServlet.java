package org.example.restaurant.servlet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.listener.OnlineUserListener;
import org.example.restaurant.listener.RequestStatListener;
import org.example.restaurant.model.Cart;
import org.example.restaurant.model.User;
import org.example.restaurant.util.MinioStore;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 当前登录用户信息（前端页面顶部导航栏与个人中心的数据回显入口）。
 * 未登录返回 loggedIn=false，前端据此渲染"登录/注册"按钮。
 */
@WebServlet(name = "meServlet", urlPatterns = "/api/auth/me")
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        ServletContext ctx = getServletContext();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loggedIn", user != null);
        data.put("user", user);
        if (user != null) {
            Cart cart = WebUtil.cart(req);
            data.put("cartCount", cart.size());
        }
        data.put("onlineCount", OnlineUserListener.count());
        data.put("appName", ctx.getAttribute(AppContextListenerHolder.APP_NAME));
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("pv", RequestStatListener.PV.get());
        runtime.put("requests", org.example.restaurant.filter.RequestLogFilter.TOTAL_REQUESTS.get());
        runtime.put("startTime", ctx.getAttribute(AppContextListenerHolder.START_TIME));
        data.put("runtime", runtime);
        Map<String, Object> infra = new LinkedHashMap<>();
        infra.put("dbReady", ctx.getAttribute(AppContextListenerHolder.DB_OK));
        infra.put("dbError", ctx.getAttribute(AppContextListenerHolder.DB_ERROR));
        infra.put("minioReady", MinioStore.isAvailable());
        infra.put("minioBucket", MinioStore.getBucket());
        data.put("infra", infra);
        WebUtil.json(resp, Resp.ok(data));
    }

    /** 常量引用，避免和 listener 包产生循环强依赖。 */
    public static final class AppContextListenerHolder {
        public static final String APP_NAME = "appName";
        public static final String START_TIME = "appStartTime";
        public static final String DB_OK = "dbReady";
        public static final String DB_ERROR = "dbError";
    }
}
