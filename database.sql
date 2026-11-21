-- ═══════════════════════════════════════════════════════════════
--  URL Shortener Service - MySQL Database Schema
--  Run this script in MySQL Workbench or terminal
--  mysql -u root -p < database.sql
-- ═══════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS url_shortener_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE url_shortener_db;

-- ───────────────────────────────────────────────────────────────
--  Table: urls
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS urls (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_url   TEXT         NOT NULL,
    short_code     VARCHAR(20)  NOT NULL UNIQUE,
    custom_alias   VARCHAR(50)  NULL UNIQUE,
    title          VARCHAR(255) NULL DEFAULT 'Untitled Link',
    clicks_count   INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,
    INDEX idx_short_code (short_code),
    INDEX idx_custom_alias (custom_alias)
);

-- ───────────────────────────────────────────────────────────────
--  Table: click_analytics
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS click_analytics (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_id           BIGINT       NOT NULL,
    clicked_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    referrer         VARCHAR(255) NULL DEFAULT 'Direct / Unknown',
    browser          VARCHAR(50)  NULL DEFAULT 'Chrome',
    operating_system VARCHAR(50)  NULL DEFAULT 'Windows',
    ip_address       VARCHAR(45)  NULL DEFAULT '127.0.0.1',
    country          VARCHAR(50)  NULL DEFAULT 'India',
    CONSTRAINT fk_analytics_url FOREIGN KEY (url_id) REFERENCES urls(id) ON DELETE CASCADE,
    INDEX idx_analytics_url (url_id),
    INDEX idx_clicked_at (clicked_at)
);

-- ───────────────────────────────────────────────────────────────
--  Seed Data (Sample links for demo & instant analytics)
-- ───────────────────────────────────────────────────────────────
INSERT IGNORE INTO urls (id, original_url, short_code, custom_alias, title, clicks_count, created_at) VALUES
(1, 'https://github.com/mittapalliindrasenareddy913-web', 'gh913', 'my-github', 'GitHub Portfolio Profile', 42, NOW() - INTERVAL 5 DAY),
(2, 'https://spring.io/projects/spring-boot', 'spboot', 'spring-docs', 'Spring Boot Official Documentation', 28, NOW() - INTERVAL 3 DAY),
(3, 'https://www.google.com', 'googl', NULL, 'Google Search Engine', 15, NOW() - INTERVAL 1 DAY);

-- Sample analytics records for link 1
INSERT IGNORE INTO click_analytics (url_id, clicked_at, referrer, browser, operating_system, ip_address, country) VALUES
(1, NOW() - INTERVAL 4 DAY, 'linkedin.com', 'Chrome', 'Windows', '127.0.0.1', 'India'),
(1, NOW() - INTERVAL 3 DAY, 'twitter.com', 'Safari', 'iOS', '127.0.0.1', 'USA'),
(1, NOW() - INTERVAL 2 DAY, 'Direct / Bookmark', 'Firefox', 'MacOS', '127.0.0.1', 'Germany'),
(1, NOW() - INTERVAL 1 DAY, 'google.com', 'Edge', 'Windows', '127.0.0.1', 'India'),
(1, NOW() - INTERVAL 2 HOUR, 'Direct / Unknown', 'Chrome', 'Android', '127.0.0.1', 'India'),

(2, NOW() - INTERVAL 2 DAY, 'github.com', 'Chrome', 'Linux', '127.0.0.1', 'India'),
(2, NOW() - INTERVAL 1 DAY, 'stackoverflow.com', 'Chrome', 'Windows', '127.0.0.1', 'UK'),

(3, NOW() - INTERVAL 6 HOUR, 'Direct / Bookmark', 'Chrome', 'Windows', '127.0.0.1', 'India');

-- ═══════════════════════════════════════════════════════════════
--  Setup Completed!
-- ═══════════════════════════════════════════════════════════════
