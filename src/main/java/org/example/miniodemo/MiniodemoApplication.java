package org.example.miniodemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用主启动类。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("org.example.miniodemo.mapper")
public class MiniodemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniodemoApplication.class, args);
    }

}
