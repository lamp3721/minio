package org.example.miniodemo.domain;

/**
 * 分片上传会话状态枚举
 */
public enum ChunkUploadStatus {
    /**
     * 已初始化，尚未上传
     */
    INIT,
    /**
     * 上传中
     */
    UPLOADING,
    
    /**
     * 上传完成，等待合并
     */
    READY_TO_MERGE,
    
    /**
     * 合并中
     */
    MERGING,
    
    /**
     * 已合并
     */
    MERGED,
    
    /**
     * 已失败
     */
    FAILED,
    
    /**
     * 已过期
     */
    EXPIRED
}