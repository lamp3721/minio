package org.example.miniodemo.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.config.MinioConfig;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.PublicAssetService;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.example.miniodemo.event.EventPublisher;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 处理公共资源（Public Assets）相关操作的服务层。
 * <p>
 * 继承了分片上传的通用逻辑，并提供了公共资源特有的功能，如生成公开访问URL。
 *
 * @see org.example.miniodemo.controller.PublicAssetController
 */
@Slf4j
@Service
public class PublicAssetServiceImpl extends AbstractChunkedFileServiceImpl implements PublicAssetService {

    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    public PublicAssetServiceImpl(ObjectStorageService objectStorageService,
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
        return bucketConfig.getPublicAssets();
    }

    @Override
    protected StorageType getStorageType() {
        return StorageType.PUBLIC;
    }

    /**
     * 为给定的对象名生成公开访问URL。
     *
     * @param objectName MinIO中的对象路径。
     * @return 完整的、可公开访问的URL。
     */
    @Override
    public String getPublicUrlFor(String objectName) {
        return minioConfig.getPublicEndpoint() + "/" + getBucketName() + "/" + objectName;
    }

    /**
     * 列出所有公开存储的文件详情。
     *
     * <p>从数据库中查询指定存储类型下的所有文件元数据，
     * 并转换为包含访问 URL 的文件详情 DTO 列表返回。
     *
     * @return 包含文件名、路径、文件大小、访问 URL 及内容类型的文件详情列表
     */
    @Override
    public List<FileDetailDto> listPublicFiles() {
        List<FileMetadata> metadataList = fileMetadataRepository.findAll(getStorageType());

        return metadataList.stream()
                .filter(Objects::nonNull)
                .map(metadata -> FileDetailDto.builder()
                        .name(metadata.getOriginalFilename())
                        .filePath(metadata.getFilePath())
                        .size(metadata.getFileSize())
                        .url(getPublicUrlFor(metadata.getFilePath()))  // 生成文件的公网访问URL
                        .contentType(metadata.getContentType())
                        .build())
                .collect(Collectors.toList());
    }

}