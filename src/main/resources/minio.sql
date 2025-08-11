/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80041 (8.0.41)
 Source Host           : localhost:3306
 Source Schema         : minio

 Target Server Type    : MySQL
 Target Server Version : 80041 (8.0.41)
 File Encoding         : 65001

 Date: 11/08/2025 12:23:10
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for file_metadata
-- ----------------------------
DROP TABLE IF EXISTS `file_metadata`;
CREATE TABLE `file_metadata`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录的唯一ID，主键',
  `folder_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '存储“桶下的文件夹路径”，如 user123/images/',
  `file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件在MinIO中存储的唯一路径/名称',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户上传时文件的原始名称',
  `file_size` bigint NOT NULL COMMENT '文件的大小，单位是字节（Bytes）',
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件的MIME类型',
  `content_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件内容的MD5哈希值',
  `bucket_name` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件所在的MinIO存储桶的名称',
  `storage_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储类型（PUBLIC 或 PRIVATE）',
  `user_id` bigint NULL DEFAULT NULL COMMENT '（预留）关联的用户ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录的创建时间',
  `last_accessed_at` timestamp NULL DEFAULT NULL COMMENT '文件最后一次被访问的时间',
  `visit_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件被访问的次数',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_hash`(`content_hash` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 174 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MinIO文件元数据表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
