package org.example.miniodemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * 添加全局CORS（跨域资源共享）规则。
     * <p>
     * 此配置允许来自配置文件中定义的源访问所有API端点。
     * 在生产环境中，应配置为具体的前端应用域名，以增强安全性。
     *
     * @param registry CORS配置注册表。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 对所有路径都启用跨域
                .allowedOrigins(allowedOrigins) // 从配置文件读取允许的源
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 允许的 HTTP 方法
                .allowedHeaders("*"); // 允许所有头部信息
    }
}