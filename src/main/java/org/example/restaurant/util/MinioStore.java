package org.example.restaurant.util;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.servlet.ServletContext;
import org.example.restaurant.config.AppConfig;

/**
 * MinIO 对象存储工具（头像、菜品图片上传）。
 * 配置来自 application.yml：endpoint / access-key / secret-key / bucket。
 * MinIO 不可用时根据 fallback-local 降级为本地磁盘 static/uploads 目录。
 */
public final class MinioStore {

    private static MinioClient client;
    private static String bucket;
    private static String endpoint;
    private static String localRoot;
    private static String contextPath = "";
    private static boolean fallbackLocal = true;
    private static boolean available = false;
    private static String lastError;

    private MinioStore() {
    }

    public static synchronized void init(AppConfig cfg, ServletContext ctx) {
        endpoint = cfg.get("minio.endpoint");
        String ak = cfg.get("minio.access-key");
        String sk = cfg.get("minio.secret-key");
        bucket = cfg.get("minio.bucket", "im-files");
        fallbackLocal = cfg.getBool("minio.fallback-local", true);
        if (ctx != null) {
            String real = ctx.getRealPath("/static/uploads");
            localRoot = real != null ? real : null;
            contextPath = ctx.getContextPath() == null || "/".equals(ctx.getContextPath()) ? "" : ctx.getContextPath();
        }
        try {
            client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(ak, sk)
                    .build();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            available = true;
            lastError = null;
            System.out.println("[MinIO] 已连接: " + endpoint + ", bucket=" + bucket);
        } catch (Exception e) {
            available = false;
            lastError = e.getMessage();
            System.err.println("[MinIO] 不可用，将降级为本地存储: " + lastError);
        }
    }

    /**
     * 上传文件，返回可访问 URL。
     *
     * @param bytes      文件字节
     * @param contentType MIME 类型
     * @param ext        扩展名（png/jpg...）
     * @param folder     目录前缀，如 avatar / dish
     */
    public static String upload(byte[] bytes, String contentType, String ext, String folder) {
        String name = folder + "/" + new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(new Date())
                + "/" + UUID.randomUUID().toString().replace("-", "") + (ext.startsWith(".") ? ext : "." + ext);
        if (available && client != null) {
            try {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(name)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(contentType)
                        .build());
                return endpoint.replaceAll("/$", "") + "/" + bucket + "/" + name;
            } catch (Exception e) {
                System.err.println("[MinIO] 上传失败，降级本地存储: " + e.getMessage());
            }
        }
        return saveLocal(bytes, name);
    }

    private static String saveLocal(byte[] bytes, String objectName) {
        try {
            if (localRoot == null) throw new IllegalStateException("无法确定本地上传目录");
            Path path = Paths.get(localRoot, objectName.split("/"));
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            //带上 context path，兼容 war 部署到 /restaurant 等子路径的场景
            return contextPath + "/static/uploads/" + objectName;
        } catch (Exception e) {
            throw new IllegalStateException("文件上传失败: " + e.getMessage(), e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String getLastError() {
        return lastError;
    }

    public static String getBucket() {
        return bucket;
    }

    public static String getEndpoint() {
        return endpoint;
    }
}
