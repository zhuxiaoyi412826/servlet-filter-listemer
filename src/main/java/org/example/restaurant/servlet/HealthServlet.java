package org.example.restaurant.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.config.AppConfig;
import org.example.restaurant.filter.RequestLogFilter;
import org.example.restaurant.listener.OnlineUserListener;
import org.example.restaurant.listener.RequestStatListener;
import org.example.restaurant.util.DbUtil;
import org.example.restaurant.util.MinioStore;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/** 健康检查：查看数据库、MinIO、配置与运行时统计。 */
@WebServlet(name = "healthServlet", urlPatterns = "/api/health")
public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ServletContext ctx = getServletContext();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("app", ctx.getAttribute("appName"));
        data.put("startTime", ctx.getAttribute("appStartTime"));

        Map<String, Object> db = new LinkedHashMap<>();
        db.put("ready", Boolean.TRUE.equals(ctx.getAttribute("dbReady")));
        db.put("error", ctx.getAttribute("dbError"));
        db.put("url", maskPassword(AppConfig.me().get("db.url")));
        db.put("username", AppConfig.me().get("db.username"));
        db.put("database", DbUtil.dbName());
        boolean ping = false;
        try (Connection c = DbUtil.open()) {
            ping = c != null && !c.isClosed() && c.isValid(2);
        } catch (SQLException ignored) {
        }
        db.put("ping", ping);
        data.put("db", db);

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("minioAvailable", MinioStore.isAvailable());
        storage.put("endpoint", MinioStore.getEndpoint());
        storage.put("bucket", MinioStore.getBucket());
        storage.put("error", MinioStore.getLastError());
        data.put("storage", storage);

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("online", OnlineUserListener.count());
        stat.put("pv", RequestStatListener.PV.get());
        stat.put("requests", RequestLogFilter.TOTAL_REQUESTS.get());
        data.put("stat", stat);
        data.put("config", AppConfig.me().snapshot());
        WebUtil.json(resp, Resp.ok(data));
    }

    private String maskPassword(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&]*", "password=******");
    }
}
