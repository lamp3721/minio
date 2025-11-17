/*
 Navicat Premium Dump SQL

 Source Server         : 192.168.0.2_3306
 Source Server Type    : MySQL
 Source Server Version : 80033 (8.0.33)
 Source Host           : 192.168.0.2:3306
 Source Schema         : minio

 Target Server Type    : MySQL
 Target Server Version : 80033 (8.0.33)
 File Encoding         : 65001

 Date: 17/11/2025 19:26:21
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chunk_upload_sessions
-- ----------------------------
DROP TABLE IF EXISTS `chunk_upload_sessions`;
CREATE TABLE `chunk_upload_sessions`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID，通常使用文件哈希值',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `file_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件哈希值',
  `file_size` bigint NOT NULL COMMENT '文件总大小',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件MIME类型',
  `folder_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标文件夹路径',
  `total_chunks` int NOT NULL COMMENT '总分片数',
  `uploaded_chunks` int NULL DEFAULT 0 COMMENT '已上传的分片数',
  `chunk_paths_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '已上传分片的路径列表（JSON格式存储）',
  `bucket_name` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储桶名称',
  `storage_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储类型（PUBLIC 或 PRIVATE）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话状态',
  `expires_at` timestamp NOT NULL COMMENT '会话过期时间',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID（可选，用于多用户场景）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `session_id`(`session_id` ASC) USING BTREE,
  INDEX `idx_session_id`(`session_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_expires_at`(`expires_at` ASC) USING BTREE,
  INDEX `idx_file_hash`(`file_hash` ASC) USING BTREE,
  CONSTRAINT `chk_status` CHECK (`status` in (_utf8mb4'INIT',_utf8mb4'UPLOADING',_utf8mb4'READY_TO_MERGE',_utf8mb4'MERGING',_utf8mb4'MERGED',_utf8mb4'FAILED',_utf8mb4'EXPIRED')),
  CONSTRAINT `chk_storage_type` CHECK (`storage_type` in (_utf8mb4'PUBLIC',_utf8mb4'PRIVATE'))
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '分片上传会话表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chunk_upload_sessions
-- ----------------------------
INSERT INTO `chunk_upload_sessions` VALUES (15, 'a67e6d97aa5f39998391f188f12ebae7', 'a67e6d_10_Sunshine_8k.jpg', 'a67e6d97aa5f39998391f188f12ebae7', 22210466, 'image/jpeg', 'file', 5, 5, '[\"a67e6d97aa5f39998391f188f12ebae7/1\",\"a67e6d97aa5f39998391f188f12ebae7/2\",\"a67e6d97aa5f39998391f188f12ebae7/3\",\"a67e6d97aa5f39998391f188f12ebae7/4\",\"a67e6d97aa5f39998391f188f12ebae7/5\"]', 'public-assets', 'PUBLIC', 'MERGED', '2025-11-18 19:22:43', NULL, '2025-11-17 19:22:43', '2025-11-17 19:22:45');
INSERT INTO `chunk_upload_sessions` VALUES (16, 'cd269bd6c98f50b82481adfd9f3f4048', '2025-10-01-raspios-trixie-arm64-lite.img.xz', 'cd269bd6c98f50b82481adfd9f3f4048', 498874192, 'application/x-compressed', 'file', 96, 96, '[\"cd269bd6c98f50b82481adfd9f3f4048/1\",\"cd269bd6c98f50b82481adfd9f3f4048/2\",\"cd269bd6c98f50b82481adfd9f3f4048/3\",\"cd269bd6c98f50b82481adfd9f3f4048/4\",\"cd269bd6c98f50b82481adfd9f3f4048/5\",\"cd269bd6c98f50b82481adfd9f3f4048/6\",\"cd269bd6c98f50b82481adfd9f3f4048/7\",\"cd269bd6c98f50b82481adfd9f3f4048/8\",\"cd269bd6c98f50b82481adfd9f3f4048/9\",\"cd269bd6c98f50b82481adfd9f3f4048/10\",\"cd269bd6c98f50b82481adfd9f3f4048/11\",\"cd269bd6c98f50b82481adfd9f3f4048/12\",\"cd269bd6c98f50b82481adfd9f3f4048/13\",\"cd269bd6c98f50b82481adfd9f3f4048/14\",\"cd269bd6c98f50b82481adfd9f3f4048/15\",\"cd269bd6c98f50b82481adfd9f3f4048/16\",\"cd269bd6c98f50b82481adfd9f3f4048/17\",\"cd269bd6c98f50b82481adfd9f3f4048/18\",\"cd269bd6c98f50b82481adfd9f3f4048/19\",\"cd269bd6c98f50b82481adfd9f3f4048/20\",\"cd269bd6c98f50b82481adfd9f3f4048/21\",\"cd269bd6c98f50b82481adfd9f3f4048/22\",\"cd269bd6c98f50b82481adfd9f3f4048/23\",\"cd269bd6c98f50b82481adfd9f3f4048/24\",\"cd269bd6c98f50b82481adfd9f3f4048/25\",\"cd269bd6c98f50b82481adfd9f3f4048/26\",\"cd269bd6c98f50b82481adfd9f3f4048/27\",\"cd269bd6c98f50b82481adfd9f3f4048/28\",\"cd269bd6c98f50b82481adfd9f3f4048/29\",\"cd269bd6c98f50b82481adfd9f3f4048/30\",\"cd269bd6c98f50b82481adfd9f3f4048/31\",\"cd269bd6c98f50b82481adfd9f3f4048/32\",\"cd269bd6c98f50b82481adfd9f3f4048/33\",\"cd269bd6c98f50b82481adfd9f3f4048/34\",\"cd269bd6c98f50b82481adfd9f3f4048/35\",\"cd269bd6c98f50b82481adfd9f3f4048/36\",\"cd269bd6c98f50b82481adfd9f3f4048/37\",\"cd269bd6c98f50b82481adfd9f3f4048/38\",\"cd269bd6c98f50b82481adfd9f3f4048/39\",\"cd269bd6c98f50b82481adfd9f3f4048/40\",\"cd269bd6c98f50b82481adfd9f3f4048/41\",\"cd269bd6c98f50b82481adfd9f3f4048/42\",\"cd269bd6c98f50b82481adfd9f3f4048/43\",\"cd269bd6c98f50b82481adfd9f3f4048/44\",\"cd269bd6c98f50b82481adfd9f3f4048/45\",\"cd269bd6c98f50b82481adfd9f3f4048/46\",\"cd269bd6c98f50b82481adfd9f3f4048/47\",\"cd269bd6c98f50b82481adfd9f3f4048/48\",\"cd269bd6c98f50b82481adfd9f3f4048/49\",\"cd269bd6c98f50b82481adfd9f3f4048/50\",\"cd269bd6c98f50b82481adfd9f3f4048/51\",\"cd269bd6c98f50b82481adfd9f3f4048/52\",\"cd269bd6c98f50b82481adfd9f3f4048/53\",\"cd269bd6c98f50b82481adfd9f3f4048/54\",\"cd269bd6c98f50b82481adfd9f3f4048/55\",\"cd269bd6c98f50b82481adfd9f3f4048/56\",\"cd269bd6c98f50b82481adfd9f3f4048/57\",\"cd269bd6c98f50b82481adfd9f3f4048/58\",\"cd269bd6c98f50b82481adfd9f3f4048/59\",\"cd269bd6c98f50b82481adfd9f3f4048/60\",\"cd269bd6c98f50b82481adfd9f3f4048/61\",\"cd269bd6c98f50b82481adfd9f3f4048/62\",\"cd269bd6c98f50b82481adfd9f3f4048/63\",\"cd269bd6c98f50b82481adfd9f3f4048/64\",\"cd269bd6c98f50b82481adfd9f3f4048/65\",\"cd269bd6c98f50b82481adfd9f3f4048/66\",\"cd269bd6c98f50b82481adfd9f3f4048/67\",\"cd269bd6c98f50b82481adfd9f3f4048/68\",\"cd269bd6c98f50b82481adfd9f3f4048/69\",\"cd269bd6c98f50b82481adfd9f3f4048/70\",\"cd269bd6c98f50b82481adfd9f3f4048/71\",\"cd269bd6c98f50b82481adfd9f3f4048/72\",\"cd269bd6c98f50b82481adfd9f3f4048/73\",\"cd269bd6c98f50b82481adfd9f3f4048/74\",\"cd269bd6c98f50b82481adfd9f3f4048/75\",\"cd269bd6c98f50b82481adfd9f3f4048/76\",\"cd269bd6c98f50b82481adfd9f3f4048/77\",\"cd269bd6c98f50b82481adfd9f3f4048/78\",\"cd269bd6c98f50b82481adfd9f3f4048/79\",\"cd269bd6c98f50b82481adfd9f3f4048/80\",\"cd269bd6c98f50b82481adfd9f3f4048/81\",\"cd269bd6c98f50b82481adfd9f3f4048/82\",\"cd269bd6c98f50b82481adfd9f3f4048/83\",\"cd269bd6c98f50b82481adfd9f3f4048/84\",\"cd269bd6c98f50b82481adfd9f3f4048/85\",\"cd269bd6c98f50b82481adfd9f3f4048/86\",\"cd269bd6c98f50b82481adfd9f3f4048/87\",\"cd269bd6c98f50b82481adfd9f3f4048/88\",\"cd269bd6c98f50b82481adfd9f3f4048/89\",\"cd269bd6c98f50b82481adfd9f3f4048/90\",\"cd269bd6c98f50b82481adfd9f3f4048/91\",\"cd269bd6c98f50b82481adfd9f3f4048/92\",\"cd269bd6c98f50b82481adfd9f3f4048/93\",\"cd269bd6c98f50b82481adfd9f3f4048/94\",\"cd269bd6c98f50b82481adfd9f3f4048/95\",\"cd269bd6c98f50b82481adfd9f3f4048/96\"]', 'public-assets', 'PUBLIC', 'MERGED', '2025-11-18 19:24:50', NULL, '2025-11-17 19:24:50', '2025-11-17 19:25:21');

-- ----------------------------
-- Table structure for file_metadata
-- ----------------------------
DROP TABLE IF EXISTS `file_metadata`;
CREATE TABLE `file_metadata`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录的唯一ID，主键',
  `folder_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '桶下的文件夹路径(区分大小写)',
  `file_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '对象完整路径(区分大小写)',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户上传时文件的原始名称',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件MIME类型',
  `content_hash` char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件内容哈希，MD5(32hex，小写)',
  `bucket_name` varchar(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '存储桶名称(ASCII，仅小写、数字、点、短横线)',
  `storage_type` enum('PUBLIC','PRIVATE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储类型',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录的创建时间',
  `last_accessed_at` timestamp NULL DEFAULT NULL COMMENT '最后一次访问时间',
  `visit_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '访问次数',
  `file_path_hash` binary(32) GENERATED ALWAYS AS (unhex(sha2(`file_path`,256))) STORED COMMENT '对象路径SHA-256(二进制32字节，用于唯一性与索引)' NULL,
  `folder_path_hash` binary(32) GENERATED ALWAYS AS (unhex(sha2(`folder_path`,256))) STORED COMMENT '文件夹路径SHA-256(二进制32字节，用于等值查询)' NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_bucket_path_hash`(`bucket_name` ASC, `file_path_hash` ASC) USING BTREE,
  INDEX `idx_bucket_folder_type_prefix`(`bucket_name` ASC, `storage_type` ASC, `folder_path`(191) ASC) USING BTREE,
  INDEX `idx_bucket_folder_hash`(`bucket_name` ASC, `storage_type` ASC, `folder_path_hash` ASC) USING BTREE,
  INDEX `idx_bucket_hash`(`bucket_name` ASC, `content_hash` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 55 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MinIO文件元数据表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of file_metadata
-- ----------------------------
INSERT INTO `file_metadata` VALUES (53, 'file', 'file/2025/11/17/a67e6d97aa5f39998391f188f12ebae7/a67e6d_10_Sunshine_8k.jpg', 'a67e6d_10_Sunshine_8k.jpg', 22210466, 'image/jpeg', 'a67e6d97aa5f39998391f188f12ebae7', 'public-assets', 'PUBLIC', '2025-11-17 19:22:44', NULL, 0, DEFAULT, DEFAULT);

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
INSERT INTO `flyway_schema_history` VALUES (1, '0', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'root', '2025-11-17 19:22:34', 0, 1);
INSERT INTO `flyway_schema_history` VALUES (2, '1', 'create file metadata table', 'SQL', 'V1__create_file_metadata_table.sql', 843992371, 'root', '2025-11-17 19:22:35', 62, 1);
INSERT INTO `flyway_schema_history` VALUES (3, '2', 'create chunk upload sessions table', 'SQL', 'V2__create_chunk_upload_sessions_table.sql', 1241855996, 'root', '2025-11-17 19:22:35', 59, 1);

SET FOREIGN_KEY_CHECKS = 1;
