package org.example.miniodemo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncFileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectStorageService objectStorageService;

    /**
     * 异步更新文件的最后访问时间。
     * <p>
     * 使用 @Async 注解，此方法将在独立的线程中执行，
     * 不会阻塞主调用线程（如文件下载请求）。
     *
     * @param filePath 文件的对象路径。
     */
    @Async
    public void updateLastAccessedTime(String filePath) {
        String hash = FilePathUtil.extractHashFromPath(filePath);
        if (hash == null) {
            log.warn("【异步任务】无法从对象路径中提取哈希值，无法更新访问时间: {}", filePath);
            return;
        }

        fileMetadataRepository.findByHash(hash, StorageType.PRIVATE).ifPresent(metadata -> {
            metadata.setLastAccessedAt(new java.util.Date());
            metadata.setVisitCount(metadata.getVisitCount() + 1);
            // 注意：这里我们依然需要一个更新方法
            // 我们将暂时假设 update 方法存在于 repository
            int updatedRows = fileMetadataRepository.update(metadata);
            if (updatedRows > 0) {
                log.info("【异步任务】文件 '{}' 的最后访问时间已更新。", filePath);
            } else {
                log.warn("【异步任务】更新文件 '{}' 的最后访问时间失败，未找到对应记录或更新失败。", filePath);
            }
        });
    }


    /**
     * 异步删除公共存储桶中的临时分片文件。
     * @param batchId 批次ID，主要用于日志记录。
     * @param filePaths 要删除的分片对象路径列表。
     * @param bucketName 存储桶名称。
     */
    @Async
    public void deleteTemporaryChunks(String batchId, List<String> filePaths, String bucketName) {
        try {
            objectStorageService.delete(bucketName, filePaths);
            log.info("【异步任务】成功删除{}库批次 '{}' 的 {} 个临时分片。", bucketName,batchId, filePaths.size());
        } catch (Exception e) {
            log.error("【异步任务】删除{}库批次 '{}' 的临时分片失败。", bucketName,batchId, e);
        }
    }

}