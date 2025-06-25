package org.example.miniodemo.dto;

import lombok.Data;

/**
 * 合并分片请求dot
 */
@Data
public class MergeRequestDto {
    private String batchId;
    private String fileName;
    private String fileHash;

    private long fileSize;
    private String contentType;
} 