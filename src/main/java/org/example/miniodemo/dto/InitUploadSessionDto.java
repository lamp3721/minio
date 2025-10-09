package org.example.miniodemo.dto;

import lombok.Data;

/**
 * 初始化上传会话请求DTO
 */
@Data
public class InitUploadSessionDto {
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
     * 总分片数
     */
    private Integer totalChunks;
    
    /**
     * 目标文件夹路径
     */
    private String folderPath = "default";
}