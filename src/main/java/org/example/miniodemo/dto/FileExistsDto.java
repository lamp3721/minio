package org.example.miniodemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于封装文件存在性检查结果的数据传输对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileExistsDto {
    /**
     * 指示文件是否存在。
     */
    private boolean exists;
    /**
     * 如果文件存在，此为文件的下载URL。
     */
    private String url;

    public FileExistsDto(boolean exists) {
        this.exists = exists;
    }
}