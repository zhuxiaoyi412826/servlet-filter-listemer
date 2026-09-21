package org.example.restaurant.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.restaurant.listener.OnlineUserListener;
import org.example.restaurant.listener.RequestStatListener;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/** 运行统计：在线人数明细、PV、接口访问排名（由 Listener 采集）。 */
@WebServlet(name = "statServlet", urlPatterns = "/api/stat/online")
public class StatServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("online", OnlineUserListener.count());
        data.put("pv", RequestStatListener.PV.get());
        List<Map<String, Object>> sessions = new ArrayList<>();
        OnlineUserListener.SESSIONS.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>(v);
            item.put("sessionId", k.substring(0, Math.min(k.length(), 8)) + "...");
            sessions.add(item);
        });
        data.put("sessions", sessions);
        List<Map<String, Object>> topUris = new ArrayList<>();
        RequestStatListener.URI_STATS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uri", e.getKey());
                    m.put("count", e.getValue().get());
                    topUris.add(m);
                });
        data.put("topUri", topUris);
        WebUtil.json(resp, Resp.ok(data));
    }
}
