package org.example.miniodemo.controller;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.service.BucketService;
import org.example.miniodemo.service.impl.BucketServiceImpl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理存储桶（Bucket）相关操作的API控制器。
 * <p>
 * 提供例如初始化应用程序所需存储桶的功能。
 */
@Slf4j
@RestController
@RequestMapping("/minio/buckets")
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;
    private final MinioBucketConfig bucketConfig;

    /**
     * 初始化应用所需的存储桶。
     * <p>
     * 此接口用于确保应用配置中定义的存储桶（私有文件桶和公共资源桶）都已存在。
     * 如果存储桶不存在，则会自动创建。这是一个幂等操作，重复调用不会产生副作用。
     *
     * @return 包含操作结果的统一响应体。成功时返回成功消息，失败时返回错误信息。
     */
    @PostMapping("/init")
    public R<String> initBuckets() {
        try {
            if (!bucketService.bucketExists(bucketConfig.getPrivateFiles())) {
                bucketService.makeBucket(bucketConfig.getPrivateFiles());
                log.info("存储桶 '{}' 创建成功。", bucketConfig.getPrivateFiles());
            }
            if (!bucketService.bucketExists(bucketConfig.getPublicAssets())) {
                bucketService.makeBucket(bucketConfig.getPublicAssets());
                log.info("存储桶 '{}' 创建成功。", bucketConfig.getPublicAssets());
            }
            log.info("存储桶初始化成功或已存在。");
            return R.success("存储桶初始化成功或已存在。");
        } catch (Exception e) {
            log.error("存储桶初始化失败: {}", e.getMessage(), e);
            return R.error(ResultCode.BUCKET_CREATION_FAILED, "存储桶初始化失败: " + e.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        initBuckets();
    }
}
