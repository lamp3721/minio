package org.example.miniodemo.domain;

/**
 * 分片上传会话状态枚举
 */
public enum ChunkUploadStatus {
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
     * 已完成
     */
    COMPLETED,
    
    /**
     * 已失败
     */
    FAILED,
    
    /**
     * 已过期
     */
    EXPIRED
}