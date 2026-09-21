package org.example.restaurant.listener;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.example.restaurant.config.AppConfig;
import org.example.restaurant.dao.AuthDao;
import org.example.restaurant.util.DbInit;
import org.example.restaurant.util.DbUtil;
import org.example.restaurant.util.MinioStore;

/**
 * 应用生命周期监听器（ServletContextListener）：
 * 1. 加载 application.yml 配置
 * 2. 初始化 MySQL：建库、建表、演示数据
 * 3. 初始化 MinIO：自动创建桶
 * 4. 开启定时清理任务（过期验证码 / 登录令牌）
 */
public class AppContextListener implements ServletContextListener {

    public static final String ATTR_START_TIME = "appStartTime";
    public static final String ATTR_DB_OK = "dbReady";
    public static final String ATTR_DB_ERROR = "dbError";
    public static final String ATTR_APP_NAME = "appName";

    private ScheduledExecutorService cleaner;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        System.out.println("====================================================");
        System.out.println("[启动] 正在初始化餐厅点餐系统...");
        LocalDateTime start = LocalDateTime.now();
        try {
            AppConfig cfg = AppConfig.load("application.yml");
            ctx.setAttribute(ATTR_APP_NAME, cfg.get("app.name", "餐厅点餐系统"));
            ctx.setAttribute("appConfig", cfg);

            // 1. MySQL 初始化
            try {
                DbUtil.init(cfg);
                String dbError = cfg.getBool("db.auto-init", true) ? DbInit.init(cfg) : null;
                boolean ok = dbError == null;
                ctx.setAttribute(ATTR_DB_OK, ok);
                ctx.setAttribute(ATTR_DB_ERROR, dbError);
                System.out.println("[启动] MySQL " + (ok ? "就绪: " + DbUtil.dbName() : "初始化失败: " + dbError));
            } catch (Exception e) {
                ctx.setAttribute(ATTR_DB_OK, false);
                ctx.setAttribute(ATTR_DB_ERROR, e.getMessage());
                System.err.println("[启动] MySQL 连接失败: " + e.getMessage());
            }

            // 2. MinIO 初始化（可选组件，失败不影响主流程）
            try {
                MinioStore.init(cfg, ctx);
                ctx.setAttribute("minioReady", MinioStore.isAvailable());
                ctx.setAttribute("minioBucket", MinioStore.getBucket());
            } catch (Exception e) {
                ctx.setAttribute("minioReady", false);
                System.err.println("[启动] MinIO 初始化异常: " + e.getMessage());
            }

            // 3. 定时清理过期数据：延迟 1 分钟，之后每 2 小时一次
            cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cleaner-thread");
                t.setDaemon(true);
                return t;
            });
            cleaner.scheduleAtFixedRate(() -> {
                try {
                    if (DbUtil.isReady()) AuthDao.cleanExpiredCodes();
                } catch (Exception ignored) {
                    // 清理任务失败不影响业务
                }
            }, 1, 120, TimeUnit.MINUTES);

        } catch (Exception e) {
            System.err.println("[启动] 致命错误: " + e.getMessage());
            ctx.setAttribute(ATTR_DB_ERROR, e.getMessage());
        }
        ctx.setAttribute(ATTR_START_TIME, start);
        System.out.println("[启动] 完成，耗时 " + java.time.Duration.between(start, LocalDateTime.now()).toMillis() + " ms");
        System.out.println("====================================================");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (cleaner != null) cleaner.shutdownNow();
        System.out.println("[停止] 应用已关闭 @ " + LocalDateTime.now());
    }
}
