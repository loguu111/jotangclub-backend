-- ============================================================
-- JotangNote 建库建表脚本
--
-- 用法：
--   mysql -uroot -p < Notes/MySQL/schema.sql
--
-- 说明：
--   1. notes.user_id 有外键指向 users.id，所以【先建 users，再建 notes】，顺序不能反；
--   2. 全部用了 IF NOT EXISTS，重复执行不会报错，也不会清空已有数据；
--   3. 字符集统一 utf8mb4（支持 emoji，笔记里写表情不会乱码）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `jotangnote`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `jotangnote`;

-- ---------- 用户表 ----------
CREATE TABLE IF NOT EXISTS `users`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键',
    `username`   VARCHAR(50)  NOT NULL COMMENT '用户名，唯一',
    `password`   VARCHAR(100) NOT NULL COMMENT '密码的 BCrypt 哈希，绝不存明文',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ---------- 笔记表 ----------
CREATE TABLE IF NOT EXISTS `notes`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `title`      VARCHAR(255) NOT NULL,
    `content`    TEXT         NOT NULL,
    `user_id`    BIGINT                DEFAULT NULL COMMENT '发布者ID，对应 users.id',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `fk_notes_user` (`user_id`),
    CONSTRAINT `fk_notes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='笔记表';
