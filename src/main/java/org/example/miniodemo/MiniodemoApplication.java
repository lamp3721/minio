package org.example.miniodemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("org.example.miniodemo.mapper")
public class MiniodemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniodemoApplication.class, args);
    }

}
