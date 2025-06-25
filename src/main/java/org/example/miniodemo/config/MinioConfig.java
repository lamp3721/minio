package org.example.miniodemo.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Data
@Configuration
public class MinioConfig {
    
    @Value("${minio.endpoint}")
    private String endpoint;
    
    @Value("${minio.public-endpoint}")
    private String publicEndpoint;
    
    @Value("${minio.access-key}")
    private String accessKey;
    
    @Value("${minio.secret-key}")
    private String secretKey;
    
    @Primary
    @Bean("internalMinioClient")
    public MinioClient internalMinioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean("publicMinioClient")
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}