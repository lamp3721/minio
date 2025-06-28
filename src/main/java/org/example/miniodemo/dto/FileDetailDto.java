package org.example.miniodemo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于封装文件详细信息的响应数据 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // JSON序列化时忽略null值的字段
public class FileDetailDto {
    /**
     * 文件在存储桶中的完整对象路径。
     */
    private String filePath;
    /**
     * 文件的原始名称。
     */
    private String name;
    /**
     * 文件大小（字节）。
     */
    private long size;
    /**
     * 文件的公开访问URL（仅对公共资源有效）。
     */
    private String url;
    /**
     * 文件的MIME类型。
     */
    private String contentType;

    /**
     * 文件被访问的次数
     */
    private Integer visitCount;
} 