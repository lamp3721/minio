-- 为已有数据库的 file_metadata 表补充 bucket_name 列（与实体保持一致）
-- 使用 MySQL 8.0 的 IF NOT EXISTS 语法，避免重复执行报错。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 增加列（若不存在）
ALTER TABLE `file_metadata`
  ADD COLUMN IF NOT EXISTS `bucket_name` VARCHAR(63) NOT NULL COMMENT '文件所在的存储桶名称' AFTER `content_hash`;

-- 按 storage_type 做回填（如有历史数据）
-- 注意：这里使用默认桶名。若你的实际桶名不同，请按需修改。
UPDATE `file_metadata`
  SET `bucket_name` = 'public-assets'
  WHERE `storage_type` = 'PUBLIC' AND (`bucket_name` IS NULL OR `bucket_name` = '');

UPDATE `file_metadata`
  SET `bucket_name` = 'private-files'
  WHERE `storage_type` = 'PRIVATE' AND (`bucket_name` IS NULL OR `bucket_name` = '');

SET FOREIGN_KEY_CHECKS = 1;