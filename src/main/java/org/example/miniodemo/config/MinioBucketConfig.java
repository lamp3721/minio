package org.example.miniodemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "minio.bucket")
public class MinioBucketConfig {
    /**
     * 用于存储私有文件的存储桶名称。
     */
    private String privateFiles;
    /**
     * 用于存储公共静态资源的存储桶名称。
     */
    private String publicAssets;
} 