package org.example.restaurant.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.example.restaurant.dao.UserDao;
import org.example.restaurant.model.User;
import org.example.restaurant.util.MinioStore;
import org.example.restaurant.util.Resp;
import org.example.restaurant.util.WebUtil;

/**
 * 头像上传：multipart -> 校验类型与大小 -> MinIO（不可用时降级本地目录）-> 更新用户表。
 */
@WebServlet(name = "avatarServlet", urlPatterns = "/api/user/avatar")
@MultipartConfig(fileSizeThreshold = 1024 * 512, maxFileSize = 1024 * 1024 * 5, maxRequestSize = 1024 * 1024 * 8)
public class AvatarServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebUtil.currentUser(req);
        if (user == null) {
            WebUtil.json(resp, 401, Resp.fail(401, "请先登录"));
            return;
        }
        Part part;
        try {
            part = req.getPart("file");
        } catch (Exception e) {
            WebUtil.json(resp, 400, Resp.fail(400, "请以 multipart/form-data 方式上传 file 字段"));
            return;
        }
        if (part == null || part.getSize() == 0) {
            WebUtil.json(resp, 400, Resp.fail(400, "请选择要上传的图片"));
            return;
        }
        String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase();
        String fileName = part.getSubmittedFileName() == null ? "" : part.getSubmittedFileName().toLowerCase();
        if (!contentType.startsWith("image/")) {
            WebUtil.json(resp, 400, Resp.fail(400, "仅支持上传图片文件（jpeg/png/gif/webp）"));
            return;
        }
        long size = part.getSize();
        if (size > 1024 * 1024 * 2) {
            WebUtil.json(resp, 400, Resp.fail(400, "图片不能超过 2MB"));
            return;
        }
        String ext = "jpg";
        if (fileName.endsWith(".png")) ext = "png";
        else if (fileName.endsWith(".gif")) ext = "gif";
        else if (fileName.endsWith(".webp")) ext = "webp";
        try (InputStream in = part.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String url = MinioStore.upload(bytes, contentType, ext, "avatar");
            UserDao.updateAvatar(user.id, url);
            User fresh = UserDao.findById(user.id);
            fresh.passwordHash = null;
            fresh.salt = null;
            req.getSession(true).setAttribute(WebUtil.SESSION_USER, fresh);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("size", size);
            data.put("storage", MinioStore.isAvailable() ? "minio" : "local");
            data.put("user", fresh);
            WebUtil.json(resp, Resp.ok(data));
        } catch (Exception e) {
            WebUtil.json(resp, 500, Resp.fail(500, "上传失败：" + e.getMessage()));
        }
    }
}
