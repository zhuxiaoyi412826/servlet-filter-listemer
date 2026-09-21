-- ============================================================
--  餐厅点餐系统 · MySQL 建库建表脚本（可选：程序启动会自动执行）
--  手动执行：mysql -uroot -p412826 < sql/schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS restaurant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE restaurant;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(128) NOT NULL COMMENT 'SHA-256(salt+password)',
    salt          VARCHAR(32)  NOT NULL,
    nickname      VARCHAR(50)  COMMENT '昵称',
    phone         VARCHAR(20)  UNIQUE COMMENT '手机号',
    avatar        VARCHAR(512) COMMENT '头像地址',
    address       VARCHAR(255) COMMENT '默认收货地址',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用（禁用后无法登录）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 菜品表
CREATE TABLE IF NOT EXISTS t_dish (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    category    VARCHAR(50)     NOT NULL COMMENT '热菜 / 凉菜 / 主食 / 汤羹 / 饮品',
    price       DECIMAL(10, 2)  NOT NULL,
    stock       INT             NOT NULL DEFAULT 100 COMMENT '库存（份数）',
    image_url   VARCHAR(512),
    description VARCHAR(500),
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(40)   NOT NULL UNIQUE COMMENT '订单号',
    user_id      BIGINT        NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    pay_type     VARCHAR(20)   NOT NULL DEFAULT 'CASH' COMMENT 'CASH 餐到付款 / WECHAT / ALIPAY',
    remark       VARCHAR(255),
    status       VARCHAR(20)   NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED / PAID / CANCELLED',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 订单明细表
CREATE TABLE IF NOT EXISTS t_order_item (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id  BIGINT         NOT NULL,
    dish_id   BIGINT         NOT NULL,
    dish_name VARCHAR(100)   NOT NULL,
    price     DECIMAL(10, 2) NOT NULL,
    quantity  INT            NOT NULL,
    amount    DECIMAL(10, 2) NOT NULL,
    KEY idx_item_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 手机验证码表
CREATE TABLE IF NOT EXISTS t_phone_code (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(20) NOT NULL,
    code       VARCHAR(10) NOT NULL,
    expire_at  DATETIME    NOT NULL,
    used       TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_code_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 记住我（Cookie 自动登录）令牌表
CREATE TABLE IF NOT EXISTS t_login_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token    VARCHAR(64) NOT NULL UNIQUE,
    user_id  BIGINT      NOT NULL,
    expire_at DATETIME   NOT NULL,
    created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 说明：初始账号与菜品演示数据由程序启动时的 DbInit 自动写入
-- （口令采用 runtime 生成的随机 salt + SHA-256，脚本内不写死哈希）：
--   管理员：admin / 123456
--   普通用户：tom / 123456
-- 若只想手工建表，执行本文件即可。
