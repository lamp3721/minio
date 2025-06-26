package org.example.miniodemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {

    /**
     * 添加全局CORS（跨域资源共享）规则。
     * <p>
     * 此配置允许来自任何源的指定HTTP方法访问所有API端点。
     * <b>注意：</b>在生产环境中，`allowedOrigins("*")` 应替换为具体的前端应用域名，以增强安全性。
     *
     * @param registry CORS配置注册表。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 对所有路径都启用跨域
                .allowedOrigins("*") // 允许的请求源   // localhost
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 允许的 HTTP 方法
                .allowedHeaders("*"); // 允许所有头部信息
    }
}