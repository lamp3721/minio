-- 创建文件元数据表
-- Flyway Version: V1
-- Description: 初始化 file_metadata 表结构
-- Date: 2025-11-07

SET NAMES utf8mb4;

-- 创建文件元数据表
CREATE TABLE IF NOT EXISTS `file_metadata` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录的唯一ID，主键',
    `folder_path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '存储桶下的文件夹路径，如 user123/images/',
    `file_path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件在MinIO中存储的唯一路径/名称',
    `original_filename` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户上传时文件的原始名称',
    `file_size` BIGINT NOT NULL COMMENT '文件的大小，单位是字节（Bytes）',
    `content_type` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件的MIME类型',
    `content_hash` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件内容的MD5哈希值',
    `bucket_name` VARCHAR(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件所在的MinIO存储桶的名称',
    `storage_type` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储类型（PUBLIC 或 PRIVATE）',
    `user_id` BIGINT NULL DEFAULT NULL COMMENT '（预留）关联的用户ID',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录的创建时间',
    `last_accessed_at` TIMESTAMP NULL DEFAULT NULL COMMENT '文件最后一次被访问的时间',
    `visit_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件被访问的次数',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_hash`(`content_hash` ASC) USING BTREE,
    INDEX `idx_bucket_name`(`bucket_name` ASC) USING BTREE,
    INDEX `idx_storage_type`(`storage_type` ASC) USING BTREE
) ENGINE = InnoDB 
  CHARACTER SET = utf8mb4 
  COLLATE = utf8mb4_unicode_ci 
  COMMENT = 'MinIO文件元数据表' 
  ROW_FORMAT = DYNAMIC;

