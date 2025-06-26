package org.example.miniodemo.service;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.example.miniodemo.config.MinioConfig;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提供定时任务，用于清理系统中的过期临时文件。
 * <p>
 * 核心功能是定期扫描并删除因上传中断或失败而残留的临时文件分片，
 * 防止这些"孤儿文件"无限期地占用存储空间，是保障系统健康的重要机制。
 */
@Slf4j
@Service
public class ScheduledCleanupService {

    private final ObjectStorageService objectStorageService;
    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    public ScheduledCleanupService(ObjectStorageService objectStorageService, MinioBucketConfig bucketConfig, MinioConfig minioConfig) {
        this.objectStorageService = objectStorageService;
        this.bucketConfig = bucketConfig;
        this.minioConfig = minioConfig;
    }

    /**
     * 定时清理孤儿分片任务。
     * <p>
     * <b>执行逻辑:</b>
     * <ol>
     *     <li>默认每天凌晨2点执行 (Cron表达式: "0 0 2 * * ?")。</li>
     *     <li>设置一个时间阈值（如24小时前），早于此阈值的临时文件将被视为过期。</li>
     *     <li>遍历私有存储桶中的所有对象。</li>
     *     <li>通过路径格式和最后修改时间筛选出过期的、未被合并的"孤儿分片"。</li>
     *     <li>批量删除这些孤儿分片，并记录结果。</li>
     * </ol>
     * 此机制确保了即使文件上传过程异常中断，残留的分片数据也最终会被自动回收。
     */
    @Scheduled(cron = "${minio.cleanup-cron}") // 从配置文件读取cron表达式
    public void cleanupOrphanedChunks() {
        log.info("开始执行孤儿分片清理任务...");

        final ZonedDateTime threshold = ZonedDateTime.now().minus(minioConfig.getChunkCleanupHours(), ChronoUnit.HOURS);
        
        try {
            // 扫描整个私有存储桶
            List<StorageObject> allObjects = objectStorageService.listObjects(
                    bucketConfig.getPrivateFiles(), null, true);

            List<String> orphanedObjectNames = allObjects.stream()
                    .filter(item -> {
                        if (item == null) return false;
                        // 检查1: 文件是否超过24小时未修改
                        boolean isOld = item.getLastModified().isBefore(threshold);
                        if (!isOld) return false;

                        // 检查2: 文件路径是否不像最终合并的文件路径
                        // 临时分片路径是 batchId/chunkNum (e.g., "uuid/0"), 不会匹配最终文件路径的正则
                        return !item.getObjectName().matches("^\\d{4}/\\d{2}/\\d{2}/.+/.+");
                    })
                    .map(StorageObject::getObjectName)
                    .collect(Collectors.toList());

            if (!orphanedObjectNames.isEmpty()) {
                log.info("发现 {} 个过期的孤儿分片，准备执行清理...", orphanedObjectNames.size());
                
                objectStorageService.delete(bucketConfig.getPrivateFiles(), orphanedObjectNames);

                log.info("孤儿分片清理完成。");
            } else {
                log.info("未发现任何需要清理的孤儿分片。");
            }

        } catch (Exception e) {
            log.error("执行孤儿分片清理任务时发生未知异常", e);
        }
    }
} 