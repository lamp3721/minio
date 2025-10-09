-- 分片上传会话表
CREATE TABLE `chunk_upload_sessions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `session_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID，通常使用文件哈希值',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_hash` VARCHAR(64) NOT NULL COMMENT '文件哈希值',
    `file_size` BIGINT NOT NULL COMMENT '文件总大小',
    `content_type` VARCHAR(100) COMMENT '文件MIME类型',
    `folder_path` VARCHAR(500) COMMENT '目标文件夹路径',
    `total_chunks` INT NOT NULL COMMENT '总分片数',
    `uploaded_chunks` INT DEFAULT 0 COMMENT '已上传的分片数',
    `chunk_paths_json` TEXT COMMENT '已上传分片的路径列表（JSON格式存储）',
    `bucket_name` VARCHAR(63) NOT NULL COMMENT '存储桶名称',
    `storage_type` VARCHAR(16) NOT NULL COMMENT '存储类型（PUBLIC 或 PRIVATE）',
    `status` VARCHAR(20) NOT NULL COMMENT '会话状态',
    `expires_at` TIMESTAMP NOT NULL COMMENT '会话过期时间',
    `user_id` BIGINT COMMENT '用户ID（可选，用于多用户场景）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_expires_at` (`expires_at`),
    INDEX `idx_file_hash` (`file_hash`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '分片上传会话表';