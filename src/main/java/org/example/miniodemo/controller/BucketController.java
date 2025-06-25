package org.example.miniodemo.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.service.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
public class BucketController {

    private final MinioService minioService;

    @GetMapping("/bucketExists")
    public ResponseEntity<?> bucketExists(@RequestParam String bucketName) {
        try {
            boolean exists = minioService.bucketExists(bucketName);
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            log.error("检查存储桶是否存在失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("检查存储桶失败: " + e.getMessage());
        }
    }

    @GetMapping("/makeBucket")
    public ResponseEntity<?> makeBucket(@RequestParam String bucketName) {
        try {
            minioService.makeBucket(bucketName);
            return ResponseEntity.ok("存储桶创建成功: " + bucketName);
        } catch (Exception e) {
            log.error("创建存储桶失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("创建存储桶失败: " + e.getMessage());
        }
    }
}
