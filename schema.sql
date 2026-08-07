-- PostgreSQL
-- 1. 先用 postgres 帳號連線，建立資料庫：
--    CREATE DATABASE flash_sale;
-- 2. 再連進 flash_sale 執行本檔其餘內容。

CREATE TABLE IF NOT EXISTS "user" (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(32)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255)   NOT NULL,
    description  TEXT,
    normal_price NUMERIC(10,2)  NOT NULL,
    total_stock  INT            NOT NULL DEFAULT 0,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS flash_sale_product (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT        NOT NULL UNIQUE,
    flash_price NUMERIC(10,2) NOT NULL,
    flash_stock INT           NOT NULL,
    version     INT           NOT NULL DEFAULT 0,
    start_time  TIMESTAMP     NOT NULL,
    end_time    TIMESTAMP     NOT NULL,
    status      INTEGER       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_time_status
    ON flash_sale_product (start_time, end_time, status);

CREATE TABLE IF NOT EXISTS flash_order (
    id                BIGSERIAL PRIMARY KEY,
    order_no          VARCHAR(64)   NOT NULL UNIQUE,
    user_id           BIGINT        NOT NULL,
    flash_product_id  BIGINT        NOT NULL,
    quantity          INT           NOT NULL DEFAULT 1,
    amount            NUMERIC(10,2) NOT NULL,
    status            INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_flash UNIQUE (user_id, flash_product_id)
);

CREATE INDEX IF NOT EXISTS idx_user_id ON flash_order (user_id);
CREATE INDEX IF NOT EXISTS idx_flash_product_id ON flash_order (flash_product_id);
CREATE INDEX IF NOT EXISTS idx_created_at ON flash_order (created_at);

-- ========== 種子資料（可選）==========
-- INSERT INTO product (name, description, normal_price, total_stock)
-- VALUES ('限量潮T', '秒殺測試商品', 100.00, 100);
--
-- INSERT INTO flash_sale_product (product_id, flash_price, flash_stock, start_time, end_time, status)
-- VALUES (1, 9.99, 100, NOW() - INTERVAL '1 minute', NOW() + INTERVAL '1 hour', 1);
