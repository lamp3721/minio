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
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.AbstractChunkedFileService;

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
 * 继承了分片上传的通用逻辑，并提供了私有文件特有的功能，如生成预签名URL和代理下载。
 *
 * @see org.example.miniodemo.controller.PrivateFileController
 */
@Slf4j
@Service
public class PrivateFileService extends AbstractChunkedFileService {

    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    public PrivateFileService(ObjectStorageService objectStorageService,
                              FileMetadataRepository fileMetadataRepository,
                              AsyncFileService asyncFileService,
                              MinioBucketConfig bucketConfig,
                              MinioConfig minioConfig) {
        super(objectStorageService, fileMetadataRepository, asyncFileService);
        this.bucketConfig = bucketConfig;
        this.minioConfig = minioConfig;
    }

    @Override
    protected String getBucketName() {
        return bucketConfig.getPrivateFiles();
    }

    @Override
    protected StorageType getStorageType() {
        return StorageType.PRIVATE;
    }

    @Override
    protected void triggerAsyncChunkCleanup(String batchId, List<String> objectNames) {
        asyncFileService.deleteTemporaryPrivateChunks(batchId, objectNames);
    }
    
    /**
     * 列出私有存储桶中所有最终合并完成的文件。
     *
     * @return 包含文件详细信息的DTO ({@link FileDetailDto}) 列表。
     */
    public List<FileDetailDto> listPrivateFiles() {
        List<FileMetadata> metadataList = fileMetadataRepository.findAll(getStorageType());

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
     * 获取私有文件的预签名下载URL（推荐的下载方式）。
     *
     * @param objectName 文件的对象路径。
     * @return 一个有访问时限的下载URL。
     * @throws Exception 如果生成URL时出错。
     */
    public String getPresignedPrivateDownloadUrl(String objectName) throws Exception {
        // 异步更新最后访问时间
        asyncFileService.updateLastAccessedTime(objectName);

        return objectStorageService.getPresignedDownloadUrl(
                getBucketName(),
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
        // 异步更新最后访问时间
        asyncFileService.updateLastAccessedTime(objectName);
        return objectStorageService.download(getBucketName(), objectName);
    }

    /**
     * 删除一个私有文件。
     *
     * @param objectName 需要删除的文件的对象路径。
     * @throws Exception 如果删除时出错。
     */
    public void deletePrivateFile(String objectName) throws Exception {
        objectStorageService.delete(getBucketName(), objectName);
        String hash = FilePathUtil.extractHashFromPath(objectName);
        if (hash != null) {
            fileMetadataRepository.deleteByHash(hash, getStorageType());
        }
    }
} 