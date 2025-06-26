package org.example.miniodemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.config.MinioConfig;
import org.example.miniodemo.controller.PublicAssetController;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.example.miniodemo.common.util.FilePathUtil;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Optional;
import java.util.Comparator;

/**
 * 处理公共资源（Public Assets）相关操作的服务层。
 * <p>
 * "公共资源"是指存储在公开访问存储桶中的对象，通常是图片、CSS等前端静态资源。
 * 本服务负责编排对象存储操作和数据库元数据记录，为上层控制器提供统一接口。
 *
 * @see PublicAssetController
 */
@Slf4j
@Service
public class PublicAssetService {

    private final ObjectStorageService objectStorageService;
    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;
    private final FileMetadataRepository fileMetadataRepository;

    public PublicAssetService(
            ObjectStorageService objectStorageService,
            MinioBucketConfig bucketConfig,
            MinioConfig minioConfig,
            FileMetadataRepository fileMetadataRepository) {
        this.objectStorageService = objectStorageService;
        this.bucketConfig = bucketConfig;
        this.minioConfig = minioConfig;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    /**
     * 检查具有特定哈希值的公共文件是否已存在。
     *
     * @param fileHash    文件的内容哈希。
     * @param storageType 存储类型（此处应为"PUBLIC"）。
     * @return 如果文件已存在，则返回true；否则返回false。
     */
    public boolean checkFileExists(String fileHash, String storageType) {
        Optional<FileMetadata> fileMetadata = fileMetadataRepository.findByHash(fileHash, StorageType.PUBLIC);
        if (fileMetadata.isPresent()) {
            log.info("【秒传检查 - 公开库】文件已存在 (hash:{})。将触发秒传。", fileHash);
            return true;
        }
        log.info("【秒传检查 - 公开库】文件不存在 (hash:{})。将执行新上传。", fileHash);
        return false;
    }

    /**
     * 检查文件是否存在，如果存在，则返回其元数据。
     *
     * @param fileHash 文件的内容哈希。
     * @return 如果文件存在，返回其 {@link FileMetadata}；否则返回 {@code null}。
     */
    public FileMetadata checkAndGetFileMetadata(String fileHash, StorageType storageType) {
        Optional<FileMetadata> fileMetadata = fileMetadataRepository.findByHash(fileHash,storageType);
        if (fileMetadata.isPresent()) {
            log.info("【秒传检查 - 公开库】文件已存在 (hash:{})。元数据已找到。", fileHash);
            return fileMetadata.get();
        }
        log.info("【秒传检查 - 公开库】文件不存在 (hash:{})。", fileHash);
        return null;
    }

    /**
     * 为给定的对象名生成公开访问URL。
     *
     * @param objectName MinIO中的对象路径。
     * @return 完整的、可公开访问的URL。
     */
    public String getPublicUrlFor(String objectName) {
        return minioConfig.getPublicEndpoint() + "/" + bucketConfig.getPublicAssets() + "/" + objectName;
    }

    /**
     * 获取公共存储桶中所有文件的列表。
     *
     * @return 包含文件详细信息的DTO ({@link FileDetailDto}) 列表。
     * @throws Exception 如果与MinIO服务器通信时发生错误。
     */
    public List<FileDetailDto> listPublicFiles() throws Exception {
        List<StorageObject> storageObjects = objectStorageService.listObjects(bucketConfig.getPublicAssets(), null, true);

        String baseUrl = minioConfig.getPublicEndpoint() + "/" + bucketConfig.getPublicAssets() + "/";

        return storageObjects.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getObjectName().matches("^\\d{4}/\\d{2}/\\d{2}/.+/.+"))
                .map(item -> {
                    String objectName = item.getObjectName();
                    // 从对象路径中提取原始文件名
                    String originalName = objectName.substring(objectName.lastIndexOf('/') + 1);

                    return FileDetailDto.builder()
                            .name(originalName)
                            .path(objectName)
                            .size(item.getSize())
                            .url(baseUrl + objectName)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 上传一个公共资源文件。
     * <p>
     * 此操作具有原子性：先将文件上传到对象存储，然后将元数据存入数据库。
     * 如果数据库操作失败，会自动触发补偿机制，删除已上传的对象。
     *
     * @param file     需要上传的文件 ({@link MultipartFile})。
     * @param fileHash 文件的内容哈希，用于秒传和路径生成。
     * @return 上传成功后文件的永久公开访问URL。
     * @throws Exception 如果上传或元数据保存失败。
     */
    public String uploadPublicImage(MultipartFile file, String fileHash) throws Exception {
        String originalFileName = file.getOriginalFilename();
        String objectName = FilePathUtil.buildDateBasedPath(originalFileName, fileHash);

        // 步骤1: 上传文件到对象存储
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    bucketConfig.getPublicAssets(),
                    objectName,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
            log.info("【文件上传 - 公共库】文件上传成功。对象路径: '{}'。", objectName);
        } catch (Exception e) {
            log.error("【文件上传 - 公共库】上传到对象存储失败。对象路径: '{}'。", objectName, e);
            throw new Exception("文件上传失败", e);
        }

        // 步骤2: 保存文件元数据到数据库，如果失败则尝试删除已上传的对象
        try {
            FileMetadata metadata = new FileMetadata();
            metadata.setObjectName(objectName);
            metadata.setOriginalFilename(originalFileName);
            metadata.setFileSize(file.getSize());
            metadata.setContentType(file.getContentType());
            metadata.setContentHash(fileHash);
            metadata.setBucketName(bucketConfig.getPublicAssets());
            metadata.setStorageType(StorageType.PUBLIC);

            boolean saved = fileMetadataRepository.save(metadata);
            if (!saved) {
                // 如果保存明确返回false，也视为一种失败
                throw new Exception("保存文件元数据失败，数据库操作未返回成功。");
            }
        } catch (Exception e) {
            log.error("【文件上传 - 公共库】保存文件元数据失败，将执行补偿操作删除MinIO对象。对象路径: '{}'。", objectName, e);
            // 补偿操作：尝试删除已上传的文件
            try {
                objectStorageService.delete(bucketConfig.getPublicAssets(), objectName);
                log.info("【文件上传 - 公共库】补偿操作成功，已删除对象: '{}'。", objectName);
            } catch (Exception deleteEx) {
                log.error("【文件上传 - 公共库】补偿操作失败，删除对象 '{}' 时发生异常。", objectName, deleteEx);
                // 此处可以记录一个需要人工干预的事件
            }
            throw new Exception("文件元数据保存失败，上传已回滚。", e);
        }

        return minioConfig.getPublicEndpoint() + "/" + bucketConfig.getPublicAssets() + "/" + objectName;
    }

    /**
     * 从公共存储桶中删除一个文件。
     * <p>
     * 此操作会先删除数据库中的元数据记录，然后再删除对象存储中的文件。
     *
     * @param objectName 需要删除的文件的对象路径。
     * @throws Exception 如果删除过程中发生错误。
     */
    public void deletePublicFile(String objectName) throws Exception {
        //从当中解析出hash
        String hash = FilePathUtil.extractHashFromPath(objectName);
        //删除元数据
        if (hash != null) {
            fileMetadataRepository.deleteByHash(hash, StorageType.PUBLIC);
        }

        objectStorageService.delete(bucketConfig.getPublicAssets(), objectName);
    }

    /**
     * 上传一个文件分片到公共存储桶的临时目录中。
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
                    bucketConfig.getPublicAssets(),
                    objectName,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        }
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
                bucketConfig.getPublicAssets(),
                batchId + "/",
                false // 只查找当前目录
        );
        return chunks.stream()
                .map(StorageObject::getObjectName)
                .map(name -> {
                    try {
                        return Integer.parseInt(name.substring(name.lastIndexOf('/') + 1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 合并指定批次的所有分片，形成一个完整的公共文件。
     *
     * @param batchId          本次上传任务的唯一批次ID。
     * @param originalFileName 文件的原始名称。
     * @param fileHash         文件的内容哈希。
     * @param contentType      文件的MIME类型。
     * @param fileSize         文件的总大小。
     * @throws Exception 如果合并过程中的任何关键步骤失败。
     */
    public String mergeChunks(String batchId, String originalFileName, String fileHash, String contentType, Long fileSize) throws Exception {
        // 1. 列出并排序所有分片
        List<StorageObject> chunks = objectStorageService.listObjects(bucketConfig.getPublicAssets(), batchId + "/", true);
        List<String> sourceObjectNames = chunks.stream()
                .map(StorageObject::getObjectName)
                .sorted(Comparator.comparing(s -> Integer.valueOf(s.substring(s.lastIndexOf('/') + 1))))
                .collect(Collectors.toList());

        if (sourceObjectNames.isEmpty()) {
            throw new Exception("找不到任何分片进行合并，批次ID: " + batchId);
        }

        // 2. 构建最终对象路径并合并
        String finalObjectName = FilePathUtil.buildDateBasedPath(originalFileName, fileHash);
        try {
            objectStorageService.compose(bucketConfig.getPublicAssets(), sourceObjectNames, finalObjectName);
        } catch (Exception e) {
            log.error("【文件合并 - 公共库】对象存储操作失败。最终对象: '{}'。", finalObjectName, e);
            throw new Exception("对象存储操作失败", e);
        }

        // 3. 记录元信息, 如果失败则执行补偿
        try {
            FileMetadata metadata = new FileMetadata();
            metadata.setObjectName(finalObjectName);
            metadata.setOriginalFilename(originalFileName);
            metadata.setFileSize(fileSize);
            metadata.setContentType(contentType);
            metadata.setContentHash(fileHash);
            metadata.setBucketName(bucketConfig.getPublicAssets());
            metadata.setStorageType(StorageType.PUBLIC);
            fileMetadataRepository.save(metadata);
        } catch (Exception e) {
            log.error("【文件合并 - 公共库】保存元数据出错，将执行补偿操作。最终对象: '{}'。", finalObjectName, e);
            try {
                objectStorageService.delete(bucketConfig.getPublicAssets(), finalObjectName);
                log.info("【文件合并 - 公共库】补偿操作成功，已删除对象: '{}'。", finalObjectName);
            } catch (Exception deleteEx) {
                log.error("【文件合并 - 公共库】补偿操作失败，删除对象 '{}' 时发生异常。", finalObjectName, deleteEx);
            }
            throw new Exception("元数据保存失败，合并已回滚", e);
        }

        // 4. 合并成功后，删除临时分片
        log.info("【文件合并 - 公共库】文件合并成功，将清理临时分片。最终对象路径: '{}'。", finalObjectName);
        deleteTemporaryChunks(batchId, sourceObjectNames);

        return getPublicUrlFor(finalObjectName);
    }

    /**
     * 合并成功后，删除指定批次的所有临时分片文件。
     *
     * @param batchId     批次ID。
     * @param objectNames 要删除的分片对象路径列表。
     */
    private void deleteTemporaryChunks(String batchId, List<String> objectNames) {
        try {
            objectStorageService.delete(bucketConfig.getPublicAssets(), objectNames);
            log.info("成功删除公共库批次 '{}' 的 {} 个临时分片。", batchId, objectNames.size());
        } catch (Exception e) {
            log.error("删除公共库批次 '{}' 的临时分片失败。", batchId, e);
        }
    }
}