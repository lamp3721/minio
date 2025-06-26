package org.example.miniodemo.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {
    
    /**
     * MinIO服务器的内部访问端点。
     */
    private String endpoint;
    
    /**
     * MinIO服务器的外部（公开）访问端点。
     */
    private String publicEndpoint;
    
    /**
     * 访问MinIO的Access Key。
     */
    private String accessKey;
    
    /**
     * 访问MinIO的Secret Key。
     */
    private String secretKey;

    /**
     * 预签名URL的过期时间（单位：分钟）。
     */
    private Integer urlExpiryMinutes;

    /**
     * 孤儿分片清理任务的阈值（单位：小时）。
     */
    private Integer chunkCleanupHours;
    
    /**
     * 定义一个主要用于内部服务间通信的MinIO客户端Bean。
     *
     * @return 配置好的 {@link MinioClient} 实例。
     */
    @Primary
    @Bean("internalMinioClient")
    public MinioClient internalMinioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 定义一个用于生成公开访问链接（如预签名URL）的MinIO客户端Bean。
     *
     * @return 配置好的 {@link MinioClient} 实例。
     */
    @Bean("publicMinioClient")
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}