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

    //这连个数据不影响合共，只是用于在数据库中记录信息
    private long fileSize;
    private String contentType;
} 