package org.example.miniodemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.controller.PrivateFileController;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.example.miniodemo.config.MinioConfig;
import org.example.miniodemo.common.util.FilePathUtil;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * 处理私有文件（Private Files）相关操作的服务层。
 * <p>
 * "私有文件"是指存储在受限访问存储桶中的对象，必须通过预签名URL或后端代理才能访问。
 * 本服务封装了所有与私有文件相关的核心业务逻辑，包括分片上传、文件合并、安全下载和删除等。
 *
 * @see PrivateFileController
 */
@Slf4j
@Service
public class PrivateFileService {

    private final ObjectStorageService objectStorageService;
    private final MinioBucketConfig bucketConfig;
    private final FileMetadataRepository fileMetadataRepository;
    private final MinioConfig minioConfig;

    public PrivateFileService(
            ObjectStorageService objectStorageService,
            MinioBucketConfig bucketConfig,
            FileMetadataRepository fileMetadataRepository,
            MinioConfig minioConfig) {
        this.objectStorageService = objectStorageService;
        this.bucketConfig = bucketConfig;
        this.fileMetadataRepository = fileMetadataRepository;
        this.minioConfig = minioConfig;
    }

    /**
     * 检查具有特定哈希值的私有文件是否已存在。
     *
     * @param fileHash    文件的内容哈希。
     * @param storageType 存储类型（此处应为"PRIVATE"）。
     * @return 如果文件存在，则返回true；否则返回false。
     */
    public boolean checkFileExists(String fileHash, StorageType storageType) {
        Optional<FileMetadata> fileMetadata = fileMetadataRepository.findByHash(fileHash, storageType);
        if (fileMetadata.isPresent()) {
            log.info("【秒传检查 - 私有库】文件已存在 (hash:{})。将触发秒传。", fileHash);
            return true;
        }
        log.info("【秒传检查 - 私有库】文件不存在 (hash:{})。将执行新上传。", fileHash);
        return false;
    }

    /**
     * 获取指定批次已上传成功的所有分片序号。
     *
     * @param batchId 本次上传任务的唯一批次ID。
     * @return 已上传分片的序号列表。
     * @throws Exception 如果查询时发生错误。
     */
    public List<Integer> getUploadedChunkNumbers(String batchId) throws Exception {
        List<StorageObject> chunks = objectStorageService.listObjects(
                bucketConfig.getPrivateFiles(),
                batchId + "/",
                false // 只查找当前目录，不递归
        );

        return chunks.stream()
                .map(StorageObject::getObjectName)
                .map(name -> {
                    try {
                        return Integer.parseInt(name.substring(name.lastIndexOf('/') + 1));
                    } catch (NumberFormatException e) {
                        // 忽略无法解析为数字的文件，例如目录本身
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 列出私有存储桶中所有最终合并完成的文件。
     * <p>
     * 此方法会通过路径格式过滤掉分片上传过程中产生的临时文件。
     *
     * @return 包含文件详细信息的DTO ({@link FileDetailDto}) 列表。
     * @throws Exception 如果列出文件时发生错误。
     */
    public List<FileDetailDto> listPrivateFiles() throws Exception {
        // 改为从数据库查询，作为文件列表的唯一真实来源
        List<FileMetadata> metadataList = fileMetadataRepository.findAll(StorageType.PRIVATE);

        return metadataList.stream()
                .filter(Objects::nonNull)
                .map(metadata -> FileDetailDto.builder()
                        .name(metadata.getOriginalFilename())
                        .path(metadata.getObjectName())
                        .size(metadata.getFileSize())
                        .contentType(metadata.getContentType())
                        .build()) // 私有文件不生成URL
                .collect(Collectors.toList());
    }

    /**
     * 合并指定批次的所有分片，形成一个完整的私有文件。
     * <p>
     * 这是一个多阶段、具有原子性的操作：
     * <ol>
     *     <li>列出并排序所有分片。</li>
     *     <li>在对象存储中将分片合并为最终文件。</li>
     *     <li>在数据库中保存文件的元数据。</li>
     *     <li>删除临时的分片文件。</li>
     * </ol>
     * 如果在保存元数据阶段失败，会自动触发补偿机制，删除已合并的最终文件。
     *
     * @param batchId          本次上传任务的唯一批次ID。
     * @param originalFileName 文件的原始名称。
     * @param fileHash         文件的内容哈希。
     * @param contentType      文件的MIME类型。
     * @param fileSize         文件的总大小。
     * @throws Exception 如果合并过程中的任何关键步骤失败。
     */
    public void mergeChunks(String batchId, String originalFileName, String fileHash, String contentType, Long fileSize) throws Exception {
        // 1. & 2.
        String finalObjectName;
        List<String> sourceObjectNames;
        try {
            // 列出该批次的所有分片
            List<StorageObject> chunks = objectStorageService.listObjects(bucketConfig.getPrivateFiles(), batchId + "/", true);

            sourceObjectNames = chunks.stream()
                    .map(StorageObject::getObjectName)
                    .sorted(Comparator.comparing(s -> Integer.valueOf(s.substring(s.lastIndexOf('/') + 1))))
                    .collect(Collectors.toList());

            if (sourceObjectNames.isEmpty()) {
                throw new MinioException("找不到任何分片进行合并，批次ID: " + batchId);
            }
            // 构建基于日期的最终对象路径
            finalObjectName = FilePathUtil.buildDateBasedPath(originalFileName, fileHash);
        } catch (Exception e) {
            log.error("合并文件失败 - 准备阶段出错（列举/排序分片）。批次ID: {}", batchId, e);
            throw new MinioException("合并文件准备阶段失败");
        }

        // 3. 将分片合并成一个新对象
        try {
            objectStorageService.compose(bucketConfig.getPrivateFiles(), sourceObjectNames, finalObjectName);
        } catch (Exception e) {
            log.error("合并文件失败 - 对象存储操作失败。最终对象: '{}'。", finalObjectName, e);
            throw new MinioException("对象存储操作失败");
        }


        // 4. 记录元信息, 如果失败则执行补偿
        try {
            FileMetadata metadata = new FileMetadata();
            metadata.setObjectName(finalObjectName);
            metadata.setOriginalFilename(originalFileName);
            metadata.setFileSize(fileSize);
            metadata.setContentType(contentType);
            metadata.setContentHash(fileHash);
            metadata.setBucketName(bucketConfig.getPrivateFiles());
            metadata.setStorageType(StorageType.PRIVATE);
            fileMetadataRepository.save(metadata);
        } catch (Exception e) {
            log.error("合并文件失败 - 保存元数据出错，将执行补偿操作。最终对象: '{}'。", finalObjectName, e);
            try {
                objectStorageService.delete(bucketConfig.getPrivateFiles(), finalObjectName);
                log.info("【文件合并 - 私有库】补偿操作成功，已删除对象: '{}'。", finalObjectName);
            } catch (Exception deleteEx) {
                log.error("【文件合并 - 私有库】补偿操作失败，删除对象 '{}' 时发生异常。", finalObjectName, deleteEx);
            }
            throw new MinioException("元数据保存失败，合并已回滚");
        }


        // 5. 合并成功后，删除临时分片
        log.info("【文件合并 - 私有库】文件合并成功，将清理临时分片。最终对象路径: '{}'。", finalObjectName);
        deleteTemporaryChunks(batchId, sourceObjectNames);
    }

    /**
     * 上传一个文件分片到私有存储桶的临时目录中。
     *
     * @param file        文件分片数据。
     * @param batchId     本次上传任务的唯一批次ID。
     * @param chunkNumber 当前分片的序号。
     * @throws Exception 如果上传分片时发生错误。
     */
    public void uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) throws Exception {
        String objectName = batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    bucketConfig.getPrivateFiles(),
                    objectName,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        }
    }

    /**
     * 获取私有文件的预签名下载URL（推荐的下载方式）。
     *
     * @param objectName 文件的对象路径。
     * @return 一个有访问时限的下载URL。
     * @throws Exception 如果生成URL时出错。
     */
    public String getPresignedPrivateDownloadUrl(String objectName) throws Exception {
        return objectStorageService.getPresignedDownloadUrl(
                bucketConfig.getPrivateFiles(),
                objectName,
                minioConfig.getUrlExpiryMinutes(),
                TimeUnit.MINUTES
        );
    }

    /**
     * 获取用于代理下载的私有文件输入流。
     *
     * @param objectName 文件的对象路径。
     * @return 文件的输入流。
     * @throws Exception 如果下载时出错。
     */
    public InputStream downloadPrivateFile(String objectName) throws Exception {
        return objectStorageService.download(bucketConfig.getPrivateFiles(), objectName);
    }

    /**
     * 删除一个私有文件。
     * <p>
     * 此操作会先删除对象存储中的文件，然后删除数据库中的元数据。
     *
     * @param objectName 需要删除的文件的对象路径。
     * @throws Exception 如果删除时出错。
     */
    public void deletePrivateFile(String objectName) throws Exception {
        // 1. 先从 MinIO 删除对象
        objectStorageService.delete(bucketConfig.getPrivateFiles(), objectName);

        // 2. 如果 MinIO 删除成功，再删除数据库元数据
        String hash = FilePathUtil.extractHashFromPath(objectName);
        if (hash != null) {
            fileMetadataRepository.deleteByHash(hash, StorageType.PRIVATE);
        }
    }

    /**
     * 合并成功后，删除指定批次的所有临时分片文件。
     *
     * @param batchId     批次ID。
     * @param objectNames 要删除的分片对象路径列表。
     */
    private void deleteTemporaryChunks(String batchId, List<String> objectNames) {
        try {
            objectStorageService.delete(bucketConfig.getPrivateFiles(), objectNames);
            log.info("成功删除批次 '{}' 的 {} 个临时分片。", batchId, objectNames.size());
        } catch (Exception e) {
            log.error("删除批次 '{}' 的临时分片失败。", batchId, e);
        }
    }
} 