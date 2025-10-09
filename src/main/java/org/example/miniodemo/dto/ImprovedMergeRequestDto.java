package org.example.miniodemo.dto;

import lombok.Data;

/**
 * 改进的文件分片合并请求DTO
 * 不再包含分片路径，由后端自行维护和验证
 */
@Data
public class ImprovedMergeRequestDto {
    /**
     * 会话ID（通常是文件哈希值）
     */
    private String sessionId;
    
    /**
     * 文件名（用于最终文件命名）
     */
    private String fileName;
    
    /**
     * 文件哈希值（用于验证完整性）
     */
    private String fileHash;
    
    /**
     * 目标文件夹路径
     */
    private String folderPath;
    
    /**
     * 预期的总分片数（用于验证）
     */
    private Integer expectedChunkCount;
}