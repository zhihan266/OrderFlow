-- =========================================================
-- OrderFlow Database Schema
-- MySQL 8.x
-- Charset: utf8mb4
-- Engine: InnoDB
-- =========================================================


-- =========================================================
-- 1. Product
-- =========================================================

CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `description` VARCHAR(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 2. SKU
-- =========================================================

CREATE TABLE IF NOT EXISTS `sku` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `sku_code` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `spec` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `price` DECIMAL(10, 2) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_sku_code` (`sku_code`),

    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 3. Inventory
-- =========================================================

CREATE TABLE IF NOT EXISTS `inventory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sku_id` BIGINT NOT NULL,
    `available_stock` INT NOT NULL DEFAULT 0,
    `reserved_stock` INT NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_sku_id` (`sku_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 4. Orders
-- =========================================================

CREATE TABLE IF NOT EXISTS `orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `user_id` BIGINT NOT NULL,
    `total_amount` DECIMAL(12, 2) NOT NULL,
    `status` VARCHAR(32) COLLATE utf8mb4_unicode_ci NOT NULL,
    `expire_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_order_no` (`order_no`),

    KEY `idx_user_id` (`user_id`),

    KEY `idx_orders_status_expire_at` (`status`, `expire_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 5. Order Item
-- =========================================================

CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `sku_id` BIGINT NOT NULL,
    `sku_code` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `sku_spec` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `unit_price` DECIMAL(10, 2) NOT NULL,
    `quantity` INT NOT NULL,
    `line_amount` DECIMAL(12, 2) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    KEY `idx_order_id` (`order_id`),

    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 6. Order Request
-- Persistent idempotency record
-- =========================================================

CREATE TABLE IF NOT EXISTS `order_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `request_hash` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `status` VARCHAR(20) COLLATE utf8mb4_unicode_ci NOT NULL,
    `order_id` BIGINT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_request`
        (`user_id`, `idempotency_key`),

    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 7. MQ Outbox
-- =========================================================

CREATE TABLE IF NOT EXISTS `mq_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `order_id` BIGINT NOT NULL,
    `event_type` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `status` VARCHAR(20) COLLATE utf8mb4_unicode_ci NOT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME DEFAULT NULL,
    `last_error` VARCHAR(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `sent_at` DATETIME DEFAULT NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_message_id` (`message_id`),

    KEY `idx_status_retry`
        (`status`, `next_retry_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;