package org.example.miniodemo.dto;

import lombok.Data;

/**
 * 用于封装直接文件上传请求的数据 DTO。
 */
@Data
public class FileUploadDto {
    /**
     * 文件的最终存储路径。默认为 "default"。
     */
    String folderPath = "default";
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