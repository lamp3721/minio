package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.service.BucketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/minio/buckets")
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;
    private final MinioBucketConfig bucketConfig;

    /**
     * POST /init : 初始化应用所需的存储桶。
     * 在应用首次启动时调用此接口，可以自动创建配置中定义的存储桶。
     */
    @PostMapping("/init")
    public ResponseEntity<String> initBuckets() {
        try {
            if (!bucketService.bucketExists(bucketConfig.getPrivateFiles())) {
                bucketService.makeBucket(bucketConfig.getPrivateFiles());
                log.info("存储桶 '{}' 创建成功。", bucketConfig.getPrivateFiles());
            }
            if (!bucketService.bucketExists(bucketConfig.getPublicAssets())) {
                bucketService.makeBucket(bucketConfig.getPublicAssets());
                log.info("存储桶 '{}' 创建成功。", bucketConfig.getPublicAssets());
                // 注意: 此处可以添加设置公开桶策略的逻辑，例如使其内容可被公开读取
            }
            return ResponseEntity.ok("存储桶初始化成功或已存在。");
        } catch (Exception e) {
            log.error("存储桶初始化失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("存储桶初始化失败: " + e.getMessage());
        }
    }
}
