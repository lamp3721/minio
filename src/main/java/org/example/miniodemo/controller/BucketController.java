package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.service.BucketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 专用于处理存储桶（Bucket）相关操作的API控制器。
 * <p>
 * 提供例如初始化应用程序所需存储桶等功能。
 */
@Slf4j
@RestController
@RequestMapping("/minio/buckets")
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;
    private final MinioBucketConfig bucketConfig;

    /**
     * POST /init : 初始化应用所需的存储桶。
     * <p>
     * 此接口设计为在应用首次启动或需要确保存储桶存在时调用。
     * 它会检查配置中定义的私有文件桶和公共资源桶是否已存在，如果不存在，则会自动创建。
     * 这是一个幂等操作，重复调用不会产生副作用。
     *
     * @return {@link ResponseEntity} 包含操作结果的响应实体。
     *         成功时返回 "存储桶初始化成功或已存在。"，并附带HTTP状态码200 (OK)。
     *         失败时返回错误信息，并附带HTTP状态码500 (Internal Server Error)。
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
                // 注意: 此处可以添加设置公开桶策略的逻辑，例如使其内容可被公开读取
            }
            return R.success("存储桶初始化成功或已存在。");
        } catch (Exception e) {
            log.error("存储桶初始化失败: {}", e.getMessage(), e);
            return R.error(ResultCode.BUCKET_CREATION_FAILED, "存储桶初始化失败: " + e.getMessage());
        }
    }
}
