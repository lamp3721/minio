package org.example.miniodemo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.MergeRequestDto;
import org.example.miniodemo.event.EventPublisher;
import org.example.miniodemo.event.FileMergedEvent;
import org.example.miniodemo.exception.BusinessException;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.AbstractChunkedFile;
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 抽象分片文件服务，封装了分片上传、合并和秒传的通用逻辑。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractChunkedFileServiceImpl implements AbstractChunkedFile {

    protected final ObjectStorageService objectStorageService;
    protected final FileMetadataRepository fileMetadataRepository;
    protected final AsyncFileService asyncFileService;
    protected final EventPublisher eventPublisher;

    // --- 抽象方法，由子类实现 ---

    /**
     * 获取当前服务操作的存储桶名称。
     */
    protected abstract String getBucketName();

    /**
     * 获取当前服务的存储类型（PUBLIC 或 PRIVATE）。
     */
    protected abstract StorageType getStorageType();


    /**
     * 检查文件是否存在
     *
     * @param fileHash 文件哈希值
     * @return 文件元数据
     */
    public Optional<FileMetadata> checkFileExists(String fileHash) {
        return fileMetadataRepository.findByHash(fileHash, getStorageType());
    }

    /**
     * 上传一个分片
     *
     * @param file        文件
     * @param batchId     批次ID
     * @param chunkNumber 分片序号
     */
    public String uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) {
        String filePath = batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    getBucketName(),
                    filePath,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        } catch (Exception e) {
            log.error("【分片上传 - {}】分片上传失败，文件路径: '{}'", getStorageType(), filePath, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "分片上传失败", e);
        }
        return filePath;
    }

    /**
     * 合并分片文件。
     * <p>
     * 此方法现在只负责对象存储层面的合并操作，并发布一个 {@link FileMergedEvent} 事件。
     * 后续的数据库持久化和分片清理将由事件监听器异步处理。
     *
     * @return 合并后的文件元数据（此时尚未持久化）。
     */
    public FileMetadata mergeChunks(MergeRequestDto mergeRequestDto) {
        // 1. 从DTO获取分片路径并按编号排序
        List<String> sourceObjectNames = mergeRequestDto.getChunkPaths().stream()
                .sorted(Comparator.comparing(s -> Integer.parseInt(s.substring(s.lastIndexOf('/') + 1))))
                .collect(Collectors.toList());

        // 如果分片列表为空，则抛出异常
        if (sourceObjectNames.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "分片列表为空，无法合并。批次ID: " + mergeRequestDto.getBatchId());
        }

        // 2. 构建最终对象路径并合并
        String finalFilePath = FilePathUtil.buildDateBasedPath(mergeRequestDto.getFolderPath(), mergeRequestDto.getFileHash(), mergeRequestDto.getFileName());
        try {
            objectStorageService.compose(getBucketName(), sourceObjectNames, finalFilePath);
            log.info("【文件合并 - {}】对象存储操作成功。最终对象: '{}'。", getStorageType(), finalFilePath);
        } catch (Exception e) {
            log.error("【文件合并 - {}】对象存储操作失败。最终对象: '{}'。", getStorageType(), finalFilePath, e);
            // 优雅地处理 MinIO 特定异常
            if (e instanceof io.minio.errors.ErrorResponseException) {
                io.minio.errors.ErrorResponseException ere = (io.minio.errors.ErrorResponseException) e;
                String code = ere.errorResponse().code();
                if ("InvalidPart".equals(code) || "InvalidPartOrder".equals(code)) {
                    throw new BusinessException(ResultCode.MERGE_INVALID_PART, "分片无效或顺序错误", e);
                } else if ("NoSuchKey".equals(code)) {
                    throw new BusinessException(ResultCode.MERGE_SOURCE_NOT_FOUND, "源分片丢失", e);
                }
            }
            // 对于其他所有异常，或非特定 MinIO 异常，可以抛出一个更通用的业务异常
            throw new BusinessException(ResultCode.UPLOAD_SESSION_STATE_MISMATCH, "文件合并失败，请检查上传状态或重试", e);
        }

        // 3. 构建元数据对象
        FileMetadata metadata = this.buildFileMetadata(mergeRequestDto, finalFilePath);

        // 4. 发布文件合并成功事件
        FileMergedEvent event = new FileMergedEvent(this, metadata, mergeRequestDto.getBatchId(), sourceObjectNames);
        eventPublisher.publish(event);
        log.info("【文件合并 - {}】文件合并成功事件已发布。最终对象路径: '{}'。", getStorageType(), finalFilePath);

        return metadata;
    }

    /**
     * 删除一个文件及其元数据。
     *
     * @param filePath 需要删除的文件的对象路径。
     */
    @Transactional
    public void deleteFile(String filePath) {
        try {
            // 1. 从对象存储中删除文件
            objectStorageService.delete(getBucketName(), filePath);

            // 2. 从数据库中删除元数据
            String hash = FilePathUtil.extractHashFromPath(filePath);
            if (hash != null) {
                fileMetadataRepository.deleteByHash(hash, getStorageType());
                log.info("【文件删除 - {}】成功删除文件元数据，Hash: {}", getStorageType(), hash);
            } else {
                log.warn("【文件删除 - {}】无法从路径中提取Hash，可能未删除元数据: {}", getStorageType(), filePath);
            }
            log.info("【文件删除 - {}】成功删除对象: {}", getStorageType(), filePath);
        } catch (Exception e) {
            log.error("【文件删除 - {}】删除对象存储文件失败: '{}'", getStorageType(), filePath, e);
            throw new BusinessException(ResultCode.FILE_DELETE_FAILED, "文件删除失败", e);
        }
    }


    // --- 私有辅助方法 ---

    /**
     * 直接上传单个文件，适用于小文件。
     *
     * @param file     上传的文件
     * @param fileHash 文件的哈希值
     * @return 文件的元数据
     */
    @Override
    @Transactional
    public FileMetadata uploadFile(String folderPath, MultipartFile file, String fileHash) {
        log.info("【直接上传 - {}】开始处理直接上传请求，文件名: {}，哈希: {}", getStorageType(), file.getOriginalFilename(), fileHash);

        // 1. 构建最终对象路径
        String finalFilePath = FilePathUtil.buildDateBasedPath(folderPath, fileHash, file.getOriginalFilename());
        log.debug("【直接上传 - {}】构建最终文件路径: {}", getStorageType(), finalFilePath);

        // 2. 上传文件到对象存储
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    getBucketName(),
                    finalFilePath,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
            log.info("【直接上传 - {}】文件已成功上传到对象存储。最终对象: '{}'。", getStorageType(), finalFilePath);
        } catch (Exception e) {
            log.error("【直接上传 - {}】文件上传到对象存储时失败。最终对象: '{}'。", getStorageType(), finalFilePath, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "文件上传失败", e);
        }

        // 3. 构建并保存文件元数据
        FileMetadata metadata = new FileMetadata();
        metadata.setFolderPath(folderPath);
        metadata.setFilePath(finalFilePath);
        metadata.setOriginalFilename(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setContentHash(fileHash);
        metadata.setBucketName(getBucketName());
        metadata.setStorageType(getStorageType());

        fileMetadataRepository.save(metadata);
        log.info("【直接上传 - {}】文件元数据已成功保存到数据库。最终对象路径: '{}'。", getStorageType(), finalFilePath);

        return metadata;
    }

    /**
     * 构建文件元数据
     *
     * @param mergeRequestDto 包含文件元数据信息的请求DTO
     * @param filePath        文件在存储中的完整路径
     * @return 文件元数据
     */
    private FileMetadata buildFileMetadata(MergeRequestDto mergeRequestDto, String filePath) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFolderPath(mergeRequestDto.getFolderPath());
        metadata.setFilePath(filePath);
        metadata.setOriginalFilename(mergeRequestDto.getFileName());
        metadata.setFileSize(mergeRequestDto.getFileSize());
        metadata.setContentType(mergeRequestDto.getContentType());
        metadata.setContentHash(mergeRequestDto.getFileHash());
        metadata.setBucketName(getBucketName());
        metadata.setStorageType(getStorageType());
        return metadata;
    }
}