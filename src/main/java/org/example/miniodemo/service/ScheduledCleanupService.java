package org.example.miniodemo.service;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提供定时任务，用于清理系统中的过期临时文件。
 * <p>
 * 这个服务的核心功能是定期扫描并删除那些因上传中断、失败或其他原因而残留的临时文件分片，
 * 从而防止这些"孤儿文件"无限期地占用存储空间。
 */
@Slf4j
@Service
public class ScheduledCleanupService {

    private final ObjectStorageService objectStorageService;
    private final MinioBucketConfig bucketConfig;

    public ScheduledCleanupService(ObjectStorageService objectStorageService, MinioBucketConfig bucketConfig) {
        this.objectStorageService = objectStorageService;
        this.bucketConfig = bucketConfig;
    }

    /**
     * 每天凌晨2点执行的定时任务，用于清理超过24小时未被合并的临时分片。
     * <p>
     * <b>执行逻辑:</b>
     * <ol>
     *     <li>设置一个24小时前的时间点作为清理阈值。</li>
     *     <li>遍历私有存储桶中的所有对象。</li>
     *     <li>检查每个对象的最后修改时间是否早于24小时前的阈值。</li>
     *     <li>同时，检查对象的路径是否不符合最终文件的格式 (YYYY/MM/DD/...)。</li>
     *     <li>同时满足以上两个条件的对象，被识别为"孤儿分片"并予以删除。</li>
     *     <li>记录被删除的分片总数。</li>
     * </ol>
     * 这个机制确保了即使文件上传过程异常中断，残留的分片数据也最终会被自动回收。
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void cleanupOrphanedChunks() {
        log.info("开始执行孤儿分片清理任务...");

        final ZonedDateTime threshold = ZonedDateTime.now().minus(24, ChronoUnit.HOURS);
        
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