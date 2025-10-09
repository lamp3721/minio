package org.example.miniodemo.dto;

import lombok.Data;
import org.example.miniodemo.domain.ChunkUploadStatus;

import java.util.List;

/**
 * 上传会话响应DTO
 */
@Data
public class UploadSessionResponseDto {
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 会话状态
     */
    private ChunkUploadStatus status;
    
    /**
     * 总分片数
     */
    private Integer totalChunks;
    
    /**
     * 已上传分片数
     */
    private Integer uploadedChunks;
    
    /**
     * 已上传的分片编号列表
     */
    private List<Integer> uploadedChunkNumbers;
}