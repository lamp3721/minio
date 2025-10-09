-- Migrate chunk_upload_sessions ENUM columns to VARCHAR with CHECK constraints
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Replace status ENUM with VARCHAR
ALTER TABLE `chunk_upload_sessions`
  MODIFY COLUMN `status` VARCHAR(20) NOT NULL,
  MODIFY COLUMN `storage_type` VARCHAR(16) NOT NULL;

-- Normalize legacy values (if any)
UPDATE `chunk_upload_sessions` SET `status`='MERGED' WHERE `status`='COMPLETED';

-- Add CHECK constraints (MySQL 8.0+ supports CHECK, otherwise skip)
ALTER TABLE `chunk_upload_sessions`
  ADD CONSTRAINT `chk_storage_type` CHECK (`storage_type` IN ('PUBLIC','PRIVATE')),
  ADD CONSTRAINT `chk_status` CHECK (`status` IN ('INIT','UPLOADING','READY_TO_MERGE','MERGING','MERGED','FAILED','EXPIRED'));

SET FOREIGN_KEY_CHECKS = 1;