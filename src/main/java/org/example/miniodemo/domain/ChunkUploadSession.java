package org.example.miniodemo.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 分片上传会话实体，用于跟踪分片上传的状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chunk_upload_sessions")
public class ChunkUploadSession extends BaseEntity {
    
    /**
     * 会话ID，通常使用文件哈希值
     */
    private String sessionId;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件哈希值
     */
    private String fileHash;
    
    /**
     * 文件总大小
     */
    private Long fileSize;
    
    /**
     * 文件MIME类型
     */
    private String contentType;
    
    /**
     * 目标文件夹路径
     */
    private String folderPath;
    
    /**
     * 总分片数
     */
    private Integer totalChunks;
    
    /**
     * 已上传的分片数
     */
    private Integer uploadedChunks;
    
    /**
     * 已上传分片的路径列表（JSON格式存储）
     */
    private String chunkPathsJson;
    
    /**
     * 存储桶名称
     */
    private String bucketName;
    
    /**
     * 存储类型
     */
    private StorageType storageType;
    
    /**
     * 会话状态
     */
    private ChunkUploadStatus status;
    
    /**
     * 会话过期时间
     */
    private LocalDateTime expiresAt;
    
    /**
     * 用户ID（可选，用于多用户场景）
     */
    private Long userId;
}