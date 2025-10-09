-- 测试会话管理功能的SQL脚本

-- 1. 检查表是否存在
SHOW TABLES LIKE 'chunk_upload_sessions';

-- 2. 查看表结构
DESCRIBE chunk_upload_sessions;

-- 3. 插入一个测试会话
INSERT INTO chunk_upload_sessions (
    session_id, file_name, file_hash, file_size, content_type, 
    folder_path, total_chunks, uploaded_chunks, chunk_paths_json,
    bucket_name, storage_type, status, expires_at, created_at, updated_at
) VALUES (
    'test-session-123', 'test.pdf', 'abc123hash', 1048576, 'application/pdf',
    'test', 3, 3, '["test-session-123/1", "test-session-123/2", "test-session-123/3"]',
    'public-assets', 'PUBLIC', 'READY_TO_MERGE', 
    DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW()
);

-- 4. 查询测试数据
SELECT * FROM chunk_upload_sessions WHERE session_id = 'test-session-123';

-- 5. 清理测试数据
-- DELETE FROM chunk_upload_sessions WHERE session_id = 'test-session-123';