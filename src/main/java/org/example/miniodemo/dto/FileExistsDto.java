package org.example.miniodemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于封装文件存在性检查结果的响应数据 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileExistsDto {
    /**
     * 标记文件是否已存在。
     */
    private boolean exists;
    /**
     * 如果文件已存在，此字段会包含其访问URL（主要用于公共资源的秒传）。
     */
    private String url;

    public FileExistsDto(boolean exists) {
        this.exists = exists;
    }
}