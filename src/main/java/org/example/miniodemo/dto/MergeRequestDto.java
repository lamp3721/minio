package org.example.miniodemo.dto;

import lombok.Data;

/**
 * 用于封装文件分片合并请求的数据 DTO。
 */
@Data
public class MergeRequestDto {
    /**
     * 文件的合并存储最终顶级路径。默认为 "default"。
     */
    String folderPath = "default";
    /**
     * 标识本次上传任务的唯一批次ID。
     */
    private String batchId;
    /**
     * 文件的原始名称。
     */
    private String fileName;
    /**
     * 完整文件的内容哈希值。
     */
    private String fileHash;
    /**
     * 文件的总大小。
     */
    private Long fileSize;
    /**
     * 文件的MIME类型。
     */
    private String contentType;
} 