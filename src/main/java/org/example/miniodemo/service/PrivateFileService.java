package org.example.miniodemo.service;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.example.miniodemo.config.MinioConfig;
import org.example.miniodemo.event.EventPublisher;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
                              EventPublisher eventPublisher,
                              MinioBucketConfig bucketConfig,
                              MinioConfig minioConfig) {
        super(objectStorageService, fileMetadataRepository, asyncFileService, eventPublisher);
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
                        .filePath(metadata.getFilePath())
                        .size(metadata.getFileSize())
                        .contentType(metadata.getContentType())
                        .visitCount(metadata.getVisitCount())
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
     * @param filePath 文件的对象路径。
     * @return 文件的输入流。
     * @throws Exception 如果下载时出错。
     */
    public InputStream downloadPrivateFile(String filePath) throws Exception {
        // 异步更新最后访问时间
        asyncFileService.updateLastAccessedTime(filePath);
        return objectStorageService.download(getBucketName(), filePath);
    }
} 