package org.example.miniodemo.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.AsyncFileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用事件监听器，负责处理文件相关的业务事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileEventListener {

    private final FileMetadataRepository fileMetadataRepository;
    private final AsyncFileService asyncFileService;

    /**
     * 监听文件合并成功事件，并持久化文件元数据。
     * <p>
     * 此操作在一个独立的事务中执行。如果元数据保存失败，事务将回滚，
     * 但这不会影响已经合并的文件对象。后续需要有补偿机制来清理这类"孤儿"文件。
     *
     * @param event 文件合并成功事件。
     */
    @EventListener
    @Transactional
    public void onFileMerged(FileMergedEvent event) {
        FileMetadata metadata = event.getFileMetadata();
        log.info("【事件监听 - 元数据】接收到文件合并事件，准备保存元数据。对象: '{}'", metadata.getObjectName());
        try {
            fileMetadataRepository.save(metadata);
            log.info("【事件监听 - 元数据】元数据保存成功。对象: '{}'", metadata.getObjectName());
        } catch (Exception e) {
            log.error("【事件监听 - 元数据】保存元数据失败。对象: '{}'，错误: {}", metadata.getObjectName(), e.getMessage(), e);
            // 抛出异常以确保事务回滚
            throw new RuntimeException("Failed to save file metadata", e);
        }
    }

    /**
     * 监听文件合并成功事件，并触发异步清理临时分片。
     *
     * @param event 文件合并成功事件。
     */
    @EventListener
    public void onFileMergedCleanup(FileMergedEvent event) {
        String batchId = event.getBatchId();
        String bucketName = event.getFileMetadata().getBucketName();
        log.info("【事件监听 - 清理】接收到文件合并事件，准备异步清理分片。批次ID: '{}', 存储桶: '{}'", batchId, bucketName);
        asyncFileService.deleteTemporaryChunks(batchId, event.getSourceObjectNames(), bucketName);
    }
} 