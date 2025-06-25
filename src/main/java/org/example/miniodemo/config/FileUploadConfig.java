package org.example.miniodemo.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.util.unit.DataSize;

import java.io.File;

@Configuration
public class FileUploadConfig {
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.parse("100MB")); // 允许单个文件最大 100MB
        factory.setMaxRequestSize(DataSize.parse("200MB")); // 允许总请求大小最大 200MB
        return factory.createMultipartConfig();
    }
}
