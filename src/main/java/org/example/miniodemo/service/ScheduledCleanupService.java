package org.example.miniodemo.service;

import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import io.minio.ListObjectsArgs;


import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCleanupService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig;
    private static final String TEMP_CHUNK_PREFIX = "tmp-chunks/";

    /**
     * 后端主动定时任务，每天凌晨3点执行。
     * 这是系统中唯一的清理机制，负责清理所有超过24小时仍未合并的孤儿分片文件。
     * cron表达式格式: [秒] [分] [时] [日] [月] [周]
     * "0 0 3 * * ?" 表示每天的3点0分0秒执行。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOrphanedChunks() {
        log.info("开始执行唯一的后端每日自动清理任务，清理孤儿分片...");

        final ZonedDateTime threshold = ZonedDateTime.now().minus(24, ChronoUnit.HOURS);
        
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketConfig.getPrivateFiles())
                            .prefix(TEMP_CHUNK_PREFIX)
                            .recursive(true)
                            .build());

            List<DeleteObject> orphanedObjects = StreamSupport.stream(results.spliterator(), false)
                    .map(itemResult -> {
                        try {
                            return itemResult.get();
                        } catch (Exception e) {
                            log.error("在清理任务中获取MinIO对象信息失败", e);
                            return null;
                        }
                    })
                    .filter(item -> {
                        if (item == null) return false;
                        try {
                            // 检查文件的最后修改时间是否早于我们设定的24小时阈值
                            return item.lastModified().isBefore(threshold);
                        } catch (Exception e) {
                             log.error("获取对象最后修改时间失败: {}", item.objectName(), e);
                            return false;
                        }
                    })
                    .map(item -> new DeleteObject(item.objectName()))
                    .collect(Collectors.toList());

            if (!orphanedObjects.isEmpty()) {
                log.info("发现 {} 个过期的孤儿分片，准备执行清理...", orphanedObjects.size());
                
                Iterable<Result<DeleteError>> deleteErrors = minioClient.removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(bucketConfig.getPrivateFiles())
                                .objects(orphanedObjects)
                                .build());
                
                for (Result<DeleteError> errorResult : deleteErrors) {
                    DeleteError error = errorResult.get();
                    log.error("删除孤儿分片时发生错误. Object: {}, Message: {}", error.objectName(), error.message());
                }
                 log.info("孤儿分片清理完成。");
            } else {
                log.info("未发现任何需要清理的孤儿分片。");
            }

        } catch (Exception e) {
            log.error("执行孤儿分片清理任务时发生未知异常", e);
        }
    }
} 