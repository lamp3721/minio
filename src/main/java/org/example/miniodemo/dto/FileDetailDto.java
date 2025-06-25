package org.example.miniodemo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于在API响应中表示文件详细信息的数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // JSON序列化时忽略null值的字段
public class FileDetailDto {
    /**
     * 文件的原始名称。
     * 例如: "photo.jpg"
     */
    private String name;

    /**
     * 文件在MinIO中的完整对象路径。
     * 例如: "2024/07/26/a3f1b2c4d5e6f7/photo.jpg"
     */
    private String path;

    /**
     * 文件的大小（字节）。
     */
    private long size;

    /**
     * 文件的公开访问URL（仅对公共资源有效）。
     * 对于私有文件，此字段将为null。
     */
    private String url;
} 