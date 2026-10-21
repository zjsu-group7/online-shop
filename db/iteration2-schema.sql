-- 在线购物系统 · 迭代 2（挑战需求 A）表结构
-- 说明：应用使用 H2 + ddl-auto=update，本脚本用于环境重建与验收查阅。
-- 兼容 H2 与 MySQL 8。

CREATE TABLE IF NOT EXISTS categories (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(50) NOT NULL,
  parent_id  BIGINT,
  sort       INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  description   CLOB,
  price         DECIMAL(10,2) NOT NULL,
  image         VARCHAR(500),
  category_id   BIGINT,
  stock         INT,
  status        VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
  frozen_by     VARCHAR(20),
  version       BIGINT,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at     TIMESTAMP,
  close_reason  VARCHAR(20),
  last_trade_failed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_images (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  url        VARCHAR(500) NOT NULL,
  sort       INT NOT NULL DEFAULT 0
);

-- 买家账号（由客户域实现对接，表结构在此约定）
CREATE TABLE IF NOT EXISTS buyers (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  username         VARCHAR(50) NOT NULL UNIQUE,
  password_hash    VARCHAR(128) NOT NULL,
  salt             VARCHAR(64) NOT NULL,
  phone            VARCHAR(30) NOT NULL,
  default_location VARCHAR(200),
  token            VARCHAR(64),
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_status      ON products(status);
CREATE INDEX IF NOT EXISTS idx_product_category    ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_product_created_at  ON products(created_at);
CREATE INDEX IF NOT EXISTS idx_image_product       ON product_images(product_id);
CREATE INDEX IF NOT EXISTS idx_category_parent     ON categories(parent_id);