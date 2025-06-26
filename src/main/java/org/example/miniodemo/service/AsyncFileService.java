package org.example.miniodemo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncFileService {

    private final FileMetadataRepository fileMetadataRepository;

    /**
     * 异步更新文件的最后访问时间。
     * <p>
     * 使用 @Async 注解，此方法将在独立的线程中执行，
     * 不会阻塞主调用线程（如文件下载请求）。
     *
     * @param objectName 文件的对象路径。
     */
    @Async
    public void updateLastAccessedTime(String objectName) {
        String hash = FilePathUtil.extractHashFromPath(objectName);
        if (hash == null) {
            log.warn("[异步任务] 无法从对象路径中提取哈希值，无法更新访问时间: {}", objectName);
            return;
        }

        fileMetadataRepository.findByHash(hash, StorageType.PRIVATE).ifPresent(metadata -> {
            metadata.setLastAccessedAt(new java.util.Date());
            // 注意：这里我们依然需要一个更新方法
            // 我们将暂时假设 update 方法存在于 repository
            int updatedRows = fileMetadataRepository.update(metadata);
            if (updatedRows > 0) {
                log.info("[异步任务] 文件 '{}' 的最后访问时间已更新。", objectName);
            } else {
                log.warn("[异步任务] 更新文件 '{}' 的最后访问时间失败，未找到对应记录或更新失败。", objectName);
            }
        });
    }
} 