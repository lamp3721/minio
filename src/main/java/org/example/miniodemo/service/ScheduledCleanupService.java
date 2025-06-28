package org.example.miniodemo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 后台定时任务服务，用于执行周期性的清理和维护操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCleanupService {

    private final ObjectStorageService objectStorageService;
    private final FileMetadataRepository fileMetadataRepository;
    private final MinioBucketConfig bucketConfig;

    /**
     * 定时清理MinIO中的孤儿文件。
     * <p>
     * “孤儿文件”是指在对象存储中存在，但在数据库中没有对应元数据记录的文件。
     * 这种情况可能在文件合并成功后，数据库写入失败时发生。
     * <p>
     * 此任务每一小时执行一次。
     */
    @Scheduled(cron = "${minio.cleanup-cron}") // 每小时执行一次
    public void cleanupOrphanMinioFiles() {
        log.info("【定时任务】开始执行MinIO孤儿文件清理任务...");
        Map<String, StorageType> bucketsToScan = Map.of(
                bucketConfig.getPublicAssets(), StorageType.PUBLIC,
                bucketConfig.getPrivateFiles(), StorageType.PRIVATE
        );

        for (Map.Entry<String, StorageType> entry : bucketsToScan.entrySet()) {
            String bucketName = entry.getKey();
            StorageType storageType = entry.getValue();
            log.info("【定时任务】正在扫描存储桶: '{}' (类型: {})", bucketName, storageType);

            try {
                List<StorageObject> objects = objectStorageService.listObjects(bucketName, "", true);
                for (StorageObject object : objects) {
                    String objectName = object.getObjectName();
                    // 尝试从路径中提取哈希。如果能提取到，说明它是一个本应有元数据的最终文件。
                    String hash = FilePathUtil.extractHashFromPath(objectName);

                    if (hash != null) {
                        // 检查数据库中是否存在对应的元数据
                        boolean metadataExists = fileMetadataRepository.findByHash(hash, storageType).isPresent();
                        if (!metadataExists) {
                            log.warn("【定时任务】发现孤儿文件！准备删除。存储桶: '{}', 对象: '{}'", bucketName, objectName);
                            try {
                                objectStorageService.delete(bucketName, objectName);
                                log.info("【定时任务】成功删除孤儿文件: '{}'", objectName);
                            } catch (Exception e) {
                                log.error("【定时任务】删除孤儿文件 '{}' 失败。", objectName, e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("【定时任务】扫描存储桶 '{}' 时发生错误。", bucketName, e);
            }
        }
        log.info("【定时任务】MinIO孤儿文件清理任务执行完毕。");
    }
} 