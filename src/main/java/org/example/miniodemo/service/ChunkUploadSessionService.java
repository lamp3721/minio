package org.example.miniodemo.service;

import org.example.miniodemo.domain.ChunkUploadSession;
import org.example.miniodemo.domain.ChunkUploadStatus;
import org.example.miniodemo.domain.StorageType;

import java.util.List;
import java.util.Optional;

/**
 * 分片上传会话服务接口
 */
public interface ChunkUploadSessionService {
    
    /**
     * 创建或获取上传会话
     */
    ChunkUploadSession createOrGetSession(String sessionId, String fileName, String fileHash, 
                                        Long fileSize, String contentType, String folderPath, 
                                        Integer totalChunks, String bucketName, StorageType storageType);
    
    /**
     * 记录分片上传成功
     */
    void recordChunkUploaded(String sessionId, Integer chunkNumber, String chunkPath);
    
    /**
     * 获取会话信息
     */
    Optional<ChunkUploadSession> getSession(String sessionId);
    
    /**
     * 获取已上传的分片路径列表
     */
    List<String> getUploadedChunkPaths(String sessionId);
    
    /**
     * 检查会话是否准备好合并
     */
    boolean isReadyToMerge(String sessionId);
    
    /**
     * 更新会话状态
     */
    void updateSessionStatus(String sessionId, ChunkUploadStatus status);
    
    /**
     * 删除会话（合并完成后清理）
     */
    void deleteSession(String sessionId);
    
    /**
     * 清理过期会话
     */
    void cleanupExpiredSessions();
}