# 味知源餐厅点餐系统

零框架实现：**JavaSE + Servlet + Filter + Listener + 原生 JDBC + MySQL + Cookie/Session + MinIO**
前端为原生 **HTML / CSS / JavaScript**，与后端完全分离（静态文件统一放在 `static` 目录，后端只提供 `/api` JSON 接口）。

## 一、技术栈说明

| 层次 | 实现 | 说明 |
| --- | --- | --- |
| Web 层 | Servlet（`HttpServlet`） | 全部通过 `@WebServlet` / `@MultipartConfig` 注解声明 |
| 拦截层 | Filter | 编码、请求日志、CORS、记住我自动登录、登录拦截、管理员鉴权 |
| 监听层 | Listener | `ServletContextListener` / `HttpSessionListener` / `ServletRequestListener` / `HttpSessionAttributeListener` |
| 会话 | Cookie + Session | 登录态 Session，"记住我"用 Cookie 令牌入库；浏览历史用 Cookie |
| 持久层 | 原生 JDBC | `DriverManager` + `PreparedStatement` + JDBC 事务（下单扣库存、取消回滚库存） |
| 数据库 | MySQL 8 | `root / 412826`，启动自动建库建表与演示数据 |
| 对象存储 | MinIO | 头像 / 菜品图片，启动自动建桶，不可用时自动降级本地磁盘 |
| 前端 | HTML + CSS + 原生 JS | `fetch` 调用接口，无任何前端框架 |
| 容器 | Jetty 11 / Tomcat 10 | `mvn jetty:run` 开发调试，或打出 war 部署 |

> 未使用 Spring、MyBatis、Jackson、SnakeYAML 等任何第三方框架，**JSON 解析与 YAML 配置解析均为手写实现**（见 `util/Json.java`、`config/AppConfig.java`）。

## 二、目录结构

```
servlet-filter-listemer/
├── pom.xml                             # 仅 4 个依赖：servlet-api / mysql-connector / minio / jetty 插件
├── README.md                           # 项目说明（含流程图）
├── md/                                 # 文档
│   ├── 01-技术架构文档.md
│   ├── 02-流程图.md                    # 13 张 Mermaid 图
│   └── 03-未来扩展规划.md
├── sql/schema.sql                      # 独立建表脚本（与 resources/db/schema.sql 同源）
└── src/main/
    ├── java/org/example/restaurant/
    │   ├── config/
    │   │   └── AppConfig.java          # 手写 YAML 解析，支持 ${ENV:default} 占位符
    │   ├── util/                       # 7 个工具类（无任何第三方库）
    │   │   ├── Json.java               # 手写 JSON 序列化/解析（反射 public 字段）
    │   │   ├── DbUtil.java             # JDBC 唯一出口：DriverManager 取连接
    │   │   ├── DbInit.java             # 建库建表、补列、灌种子数据
    │   │   ├── PasswordUtil.java       # SHA-256(salt + 明文)
    │   │   ├── WebUtil.java            # 写 JSON 响应、取请求体、参数校验
    │   │   ├── Resp.java               # 统一响应体 {code, msg, data}
    │   │   └── MinioStore.java         # 对象存储上传 + 本地降级
    │   ├── model/                      # 6 个实体：public 字段，无 getter/setter
    │   │   └── User / Dish / Cart / CartItem / Order / OrderItem
    │   ├── dao/                        # 原生 JDBC，手写 SQL（4 个）
    │   │   ├── AuthDao.java            # 验证码、登录令牌
    │   │   ├── UserDao.java            # 用户 CRUD、后台用户管理
    │   │   ├── DishDao.java            # 菜品 CRUD、库存扣减
    │   │   └── OrderDao.java           # 下单事务、状态流转、营收聚合
    │   ├── filter/                     # 6 个过滤器（web.xml 声明顺序即执行顺序）
    │   │   └── Encoding / RequestLog / Cors / RememberMe / Auth / Admin
    │   ├── listener/                   # 4 个监听器
    │   │   ├── AppContextListener      # 启动：加载 yml → 建表 → MinIO 建桶
    │   │   ├── OnlineUserListener      # 会话级：在线人数
    │   │   ├── RequestStatListener     # 请求级：PV / 耗时统计
    │   │   └── LoginAuditListener      # 属性级：登录审计
    │   ├── servlet/                    # 18 个 @WebServlet（注解注册，不在 web.xml）
    │   │   ├── 认证：Register / Login / Logout / PhoneCode / PhoneLogin / Me
    │   │   ├── 业务：Dish / Cart / Order / User / Avatar
    │   │   ├── 后台：AdminDish / AdminOrder / AdminUser / AdminIncome
    │   │   └── 运维：Health / Stat / Error
    │   └── demo/
    │       └── UserServlet1.java       # 教学 Demo：表单查 t_user 判登录成败
    ├── resources/
    │   ├── application.yml             # 数据库与 MinIO 配置（支持环境变量覆盖）
    │   └── db/schema.sql               # 6 张表 DDL，启动自动执行
    └── webapp/
        ├── WEB-INF/web.xml             # Listener / Filter / welcome-file / 错误页
        └── static/                     # 前后端分离的静态前端（8 个页面）
            ├── index.html    点餐首页
            ├── login.html / register.html    登录 / 注册
            ├── orders.html   我的订单
            ├── profile.html  个人中心
            ├── admin.html    后台管理（菜品/订单/用户/收入）
            ├── demo.html     Servlet 教学 Demo 页
            ├── 404.html      错误页
            ├── css/common.css
            └── js/          10 个：api.js（fetch 封装）index / auth / orders /
                             profile / admin / admin-common / admin-order /
                             admin-user / admin-income
```

## 三、运行步骤

### 1. 准备环境

- JDK 21、Maven 3.9+
- MySQL 8：账号 `root`、密码 `412826`（若不同，修改 `application.yml` 的 `db.password`）
- MinIO（可选）：默认 `http://127.0.0.1:9000`，账号密码 `minioadmin/minioadmin`，桶 `im-files`

### 2. 修改配置：`src/main/resources/application.yml`

```yaml
db:
  url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/restaurant?...}
  username: ${DB_USERNAME:root}
  password: ${DB_PASSWORD:412826}
  auto-init: true          # 启动自动建库 / 建表 / 灌入演示数据

minio:
  endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:im-files}
  fallback-local: true     # MinIO 不可用时降级保存到 static/uploads
```

所有配置项都支持环境变量覆盖，例如：`set MINIO_ENDPOINT=http://192.168.1.9:9000`。

### 3. 启动

```bash
# 方式一：内嵌 Jetty 运行（默认端口 8081，可通过 -Djetty.port=xxxx 修改）
mvn clean compile jetty:run
# 浏览器访问：http://127.0.0.1:8081/

# 方式二：打成 war 部署到 Tomcat 10
mvn clean package
# 把 target/restaurant.war 拷到 tomcat/webapps 下即可（访问路径 /restaurant）
```

### 4. 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | 123456 | 管理员（可进入后台管理） |
| tom | 123456 | 普通用户 |

> 演示环境未接短信网关，手机号验证码会直接回显在页面上，同时打印在控制台日志中。

## 四、核心功能

- **注册**：服务端全量校验（长度、格式、重复），失败时返回 `errors` 与已填 `form` 数据，前端字段级高亮并回显
- **登录**：用户名 / 手机号均可；勾选"记住我"下发 `rm_token` Cookie（30 天），由 `RememberMeFilter` 实现关浏览器后自动登录
- **手机号登录**：`/api/auth/phone/code` 获取验证码 → `/api/auth/phone/login` 登录，未注册手机号自动建号
- **点餐**：菜品分类筛选、关键字搜索、加入购物车（Session 存储），库存校验
- **下单**：JDBC 事务同时写订单、明细并扣减库存，失败整体回滚；取消订单自动返还库存
- **个人中心**：资料回显、修改，头像上传 MinIO 后实时刷新
- **后台管理**：菜品增删改查与上下架（ADMIN 角色），支持表单校验回显
- **运维信息**：`/api/health` 查看 MySQL / MinIO 连通性；在线人数、PV 由 Listener 实时统计

## 五、主要接口

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 注册（失败回显 form + errors） | 否 |
| POST | `/api/auth/login` | 账号密码登录（记住我） | 否 |
| POST | `/api/auth/phone/code` | 获取短信验证码 | 否 |
| POST | `/api/auth/phone/login` | 手机号验证码登录/自动注册 | 否 |
| GET | `/api/auth/logout` | 退出登录 | 是 |
| GET | `/api/auth/me` | 当前用户、购物车数量、在线人数 | 否（自动判断） |
| GET | `/api/dishes` | 菜品列表（category / keyword / id） | 否 |
| GET/POST/DELETE | `/api/cart` | 购物车增删改 | 是 |
| GET/POST/DELETE | `/api/orders` | 订单列表 / 下单 / 取消 | 是 |
| GET/POST | `/api/user/profile` | 资料回显与保存 | 是 |
| POST | `/api/user/avatar` | 头像上传（multipart → MinIO） | 是 |
| GET/POST/PUT/DELETE | `/api/admin/dish` | 菜品管理 | 管理员 |
| GET | `/api/health` | 健康检查 | 否 |
| GET | `/api/stat/online` | 在线人数、PV、接口访问排行 | 否 |

统一响应结构：`{ "code": 0, "msg": "ok", "data": {...} }`，`code != 0` 表示业务失败。

## 六、项目文档（md 目录）

| 文档 | 内容 |
| --- | --- |
| [md/01-技术架构文档.md](md/01-技术架构文档.md) | 分层架构、包职责、核心类说明、设计取舍 |
| [md/02-流程图.md](md/02-流程图.md) | 13 张 Mermaid 流程图：部署拓扑、启动初始化、过滤器链、两种登录、下单事务、订单状态机、文件上传、统计旁路、错误处理、后台四大模块，**以及全链路时序图（第 12 节）与分层数据流向图（第 13 节）** |
| [md/03-未来扩展规划.md](md/03-未来扩展规划.md) | 后续演进方向：连接池、Service 层、Redis、Spring Boot 迁移等 |

> 阅读器不支持 Mermaid 时，可把代码块粘到 https://mermaid.live 查看。

## 七、常见问题

- **8081 端口被占用**：`mvn jetty:run -Djetty.port=8090`
- **MySQL 连接失败**：确认服务已启动、账号密码为 `root/412826`，或执行 `sql/schema.sql` 手工建表；排查接口 `GET /api/health`
- **MinIO 不可用**：不影响点餐主流程，头像会自动降级保存到本地 `webapp/static/uploads`
- **数据表重建**：删除 `restaurant` 库后重启应用，会自动重新初始化（含 12 道演示菜品）

---

## 八、核心流程图

### 各层职责与现状

| 层次     | 本项目实现                         | 说明                                              |
| -------- | ---------------------------------- | ------------------------------------------------- |
| 表现层   | `static/*.html` + `static/js/*.js` | 原生 HTML/JS，无框架；`api.js` 统一封装 fetch     |
| 过滤器层 | `filter/` 6 个                     | 编码、日志、跨域、自动登录、鉴权、管理员权限      |
| 控制器层 | `servlet/` 18 个                   | `@WebServlet` 注解注册，负责取参 / 校验 / 响应    |
| 持久层   | `dao/` 4 个                        | 手写 JDBC + `PreparedStatement`，无 ORM、无连接池 |
| 数据源   | `util/DbUtil`                      | 唯一 `DriverManager.getConnection` 出口           |
| 数据库   | MySQL 8                            | 6 张表，DDL 在 `db/schema.sql`                    |

### 1. 全链路请求时序（前台 → 过滤器 → Servlet → DAO → 数据库 → 返回）

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant P as 前台页面<br/>static/*.html
    participant J as 前端封装<br/>static/js/api.js
    participant C as Web 容器<br/>Tomcat 10 / Jetty 11
    participant F as 过滤器链<br/>Filter Chain
    participant S as 控制器<br/>@WebServlet（18 个）
    participant D as 持久层<br/>dao/*Dao
    participant K as 连接工具<br/>util/DbUtil
    participant DB as 数据库<br/>MySQL 8（restaurant）

    U->>P: 点击按钮 / 提交表单
    P->>J: App.get('/api/dishes')
    J->>J: 自动识别 contextPath（BASE）<br/>fetch(credentials:'same-origin')
    J->>C: GET /servlet_filter_listemer_war/api/dishes

    C->>F: 进入过滤器链（web.xml 声明顺序）
    F->>F: ① EncodingFilter（UTF-8）
    F->>F: ② RequestLogFilter（记录耗时）
    F->>F: ③ CorsFilter（跨域响应头）
    F->>F: ④ RememberMeFilter（无 session<br/>但有 rm_token 时重建会话）

    alt 路径匹配 /api/*
        F->>F: ⑤ AuthFilter 校验登录态
        alt 未登录 且 不在白名单
            F-->>J: 401 {"code":401}
            J->>P: 跳转 /static/login.html?redirect=...
        end
        opt 路径匹配 /api/admin/*
            F->>F: ⑥ AdminFilter 校验 role=ADMIN
            F-->>J: 403（非管理员）
        end
    end

    F->>S: chain.doFilter() 放行 → 分发到目标 Servlet
    S->>S: 取参数 / 参数校验 / 业务判断
    Note over S: ⚠ 本项目无 service 层<br/>Servlet 直接调用 DAO<br/>（如需可在此插入 XxxService）
    S->>D: UserDao.findByAccount(input)
    D->>K: DbUtil.open()
    K->>DB: DriverManager.getConnection(...)
    DB-->>K: Connection
    K-->>D: Connection
    D->>DB: PreparedStatement<br/>SELECT ... WHERE username = ? OR phone = ?
    DB-->>D: ResultSet
    D->>D: map(rs) → Model 实体（手写映射，无 ORM）
    D-->>S: 返回 User / List / Map

    S->>S: 组装 Resp.ok(data)
    S-->>J: 200 {"code":0,"msg":"ok","data":{...}}<br/>WebUtil.writeJson()
    J->>J: 解析 {code,msg,data}
    J-->>P: 渲染 DOM / toast 提示
    P-->>U: 页面展示结果
```

### 2. 请求进入：Filter 拦截链

```mermaid
flowchart TD
    R(["HTTP 请求"]) --> F1["EncodingFilter<br/>设置 UTF-8"]
    F1 --> F2["RequestLogFilter<br/>记录 ip/uri/起始时间"]
    F2 --> F3["CorsFilter<br/>写跨域头，OPTIONS 直接返回"]
    F3 --> F4{"RememberMeFilter<br/>Session 里已有用户？"}
    F4 -->|"无 且 有 rm_token"| F4a["AuthDao.findUserByToken()"]
    F4a --> F4b{"令牌有效且未禁用？"}
    F4b -->|"是"| F5["重建 Session"]
    F4b -->|"否"| F4c["清除 Cookie"]
    F4 -->|"已有"| F5
    F4c --> F5
    F5 --> F6{"AuthFilter<br/>路径在白名单？"}
    F6 -->|"是"| S1["直接放行到 Servlet"]
    F6 -->|"否"| F6a{"Session 有用户？"}
    F6a -->|"否"| E401(["401 未登录"])
    F6a -->|"是"| F7{"路径为 /api/admin/* ?"}
    F7 -->|"否"| S1
    F7 -->|"是"| F7a{"role == ADMIN ?"}
    F7a -->|"否"| E403(["403 无权限"])
    F7a -->|"是"| S1
    S1 --> S2["Servlet 业务处理"]
    S2 --> S3["RequestLogFilter 回写<br/>status / cost"]
    S3 --> E(["JSON 响应"])
```

---

### 3. 登录：账号密码 + 记住我

<img src="https://zhuxiaoyi-1300958454.cos.ap-guangzhou.myqcloud.com/img/mermaid (13).png" style="zoom:150%;" />

### 4. 登录：手机号验证码（首次自动注册）

```mermaid
flowchart TD
    A["用户：输入手机号"] --> B["GET /api/auth/phone/code"]
    B --> C["生成 6 位随机码 + 有效期"]
    C --> D["INSERT t_phone_code<br/>(phone, code, expire_at, used=0)"]
    D --> E["(演示环境) 控制台输出验证码"]
    E --> F["用户填写验证码"]
    F --> G["POST /api/auth/phone/login"]
    G --> H{"验证码有效且未过期/未使用？"}
    H -->|"否"| H1(["400 验证码错误或已失效"])
    H -->|"是"| I{"手机号已注册？"}
    I -->|"否"| J["自动创建账号<br/>默认昵称 phone_xxxx"]
    I -->|"是"| K["查出已有用户"]
    J --> L["标记 code.used = 1"]
    K --> L
    L --> M["写入 Session，返回登录态"]
    M --> N(["跳转首页"])
```

---

### 5. 浏览菜品 → 加购 → 下单（核心链路，含事务与库存）

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant FE as index.html / app.js
    participant DS as DishServlet
    participant CS as CartServlet
    participant OS as OrderServlet
    participant OD as OrderDao
    participant DD as DishDao
    participant DB as MySQL

    U->>FE: 打开点餐页
    FE->>DS: GET /api/dishes?category=
    DS->>DD: list(category, status=1)
    DD->>DB: SELECT ... FROM t_dish WHERE status=1
    DB-->>FE: 菜品卡片渲染

    U->>FE: 点击"加入购物车"
    FE->>CS: POST /api/cart {dishId, qty}
    CS->>DD: findById(dishId)
    alt 已下架 / 库存不足
        CS-->>FE: 400 提示
    else 正常
        CS->>CS: Session 购物车累加（内存）
        CS-->>FE: {cartCount, cart}
    end

    U->>FE: 点击"去结算"
    FE->>OS: POST /api/orders {payType, remark}
    OS->>OD: create(userId, cart, payType, remark)
    OD->>DB: setAutoCommit(false)
    OD->>DB: SELECT ... FOR UPDATE（锁定购物车涉及的菜品行）
    OD->>OD: 校验存在性 / 上架状态 / 库存充足
    alt 任一校验失败
        OD->>DB: ROLLBACK
        OD-->>OS: IllegalArgumentException
        OS-->>FE: 400（前端保留购物车数据便于修改）
    else 全部通过
        OD->>DB: UPDATE t_dish SET stock = stock - qty
        OD->>DB: INSERT t_order（order_no，status='CREATED'）
        OD->>DB: INSERT t_order_item（快照菜名与单价）
        OD->>DB: COMMIT
        OD-->>OS: Order（含明细）
        OS->>OS: 清空 Session 购物车
        OS-->>FE: {order, cartCount:0} → 跳转 orders.html
    end
```



### 6. 错误处理统一出口

```mermaid
flowchart TD
    A["任意 Servlet 抛异常"] --> B["web.xml error-page<br/>exception-type Throwable"]
    B --> C["转发到 /api/error"]
    C --> D["ErrorServlet 取 ERROR_STATUS_CODE / EXCEPTION"]
    D --> E{"是否 API 请求？"}
    E -->|"是"| F(["JSON：{code, msg}"])
    E -->|"否（静态资源 404）"| G(["forward /static/404.html"])
    F --> H["前端 App.toast 统一提示"]
    G --> H
```

---

### 7. 后台管理四大模块（数据流）

```mermaid
flowchart TB
    subgraph ADMIN["admin.html 四大 Tab"]
        T1["菜品管理"]
        T2["订单管理"]
        T3["用户管理"]
        T4["收入管理"]
    end
    T1 --> A1["/api/admin/dish<br/>GET / POST / PUT / DELETE"]
    T2 --> A2["/api/admin/orders<br/>GET（分页+筛选+概览）/ PUT（改状态）"]
    T3 --> A3["/api/admin/user<br/>GET（分页+角色/状态筛选）/ PUT（角色/启禁/重置密码）"]
    T4 --> A4["/api/admin/income?days=N<br/>GET（日营收曲线 / 客单价 / Top 菜品 / 取消金额）"]

    A1 --> D1[("t_dish")]
    A2 --> D2[("t_order + t_order_item")]
    A3 --> D3[("t_user")]
    A4 --> D4[("聚合查询：SUM / GROUP BY date")]

    A1 --> M1[("MinIO / local")]
    A2 --> M2["订单状态机 + 库存回滚"]

    D4 --> V["折线图 / 卡片 <br/>原生 JS 渲染，无图表库"]
```

# 项目流程

> 图形仅为流程示意，具体实现以源码为准：`servlet/` 处理流程、`filter/` 拦截、`dao/` 事务、`listener/` 旁路统计。
> 若阅读器不支持 Mermaid，可用 https://mermaid.live 粘贴查看。





```
前端 fetch
  ↓
① Listener（RequestStatListener 记录请求开始）
  ↓
② Filter 链：Encoding → RequestLog → Cors → RememberMe → Auth → Admin
     ├─ 不通过：直接写 401/403 JSON 返回（Servlet 不会执行）
     └─ 通过：chain.doFilter() → 进入 Servlet
  ↓
③ Servlet：取参数 → 调 Dao
  ↓
④ Dao：DbUtil.open() 拿连接 → PreparedStatement 执行 SQL
  ↓
⑤ ResultSet → model 对象（Dao 里的 map(rs)）
  ↓
⑥ Json.toJson（反射 public 字段）→ WebUtil.json 写响应
  ↓
⑦ Filter 链回溯（RequestLogFilter 打印 status、耗时）
  ↓
⑧ Listener（请求销毁，统计收尾）
     ↓
  异常逃出 ③ → 容器 → ErrorServlet → 500 JSON（仅兜底）
```

```mermaid
graph TD
    A[前端 fetch] --> B["① Listener RequestStatListener\n记录请求开始"]
    B --> C["② Filter过滤链\nEncoding → RequestLog → Cors → RememberMe → Auth → Admin"]

    C -->|"校验不通过"| C1["直接输出401/403 JSON响应\nServlet不再执行"] --> H["⑦ Filter链回溯\nRequestLogFilter打印状态码、请求耗时"]
    C -->|"校验通过，执行chain.doFilter()"| D["③ Servlet\n接收请求参数，调用Dao"]

    D -->|"业务异常向外抛出"| ERR["异常逃出Servlet"] --> ERR1["Web容器转发 ErrorServlet\n输出兜底500 JSON"] --> H

    D --> E["④ Dao层\nDbUtil.open 获取连接\nPreparedStatement执行SQL"]
    E --> F["⑤ ResultSet结果集\nmap rs 封装为model实体对象"]
    F --> G["⑥ Json.toJson反射序列化public字段\nWebUtil.json写出http响应"]

    G --> H
    H --> I["⑧ Listener请求销毁\n请求统计收尾"]
    I --> J["返回最终数据给前端"]
```

> 一句话概括你的理解："Filter 守卫、Servlet 调度、Dao 执行、DbUtil 供连接、model 承载、Json 序列化、Listener 观测" —— 这个分工描述是准确的，只需把"拦截"理解成"可放行也可终止"，把"不合格走全局异常"改成"不合格由 Filter 直接响应"。





流程架构图

![](https://zhuxiaoyi-1300958454.cos.ap-guangzhou.myqcloud.com/img/mermaid (12).png)