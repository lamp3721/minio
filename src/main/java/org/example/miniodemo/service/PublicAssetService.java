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
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.AbstractChunkedFileService;
import org.example.miniodemo.event.EventPublisher;

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
 * 继承了分片上传的通用逻辑，并提供了公共资源特有的功能，如生成公开访问URL。
 *
 * @see org.example.miniodemo.controller.PublicAssetController
 */
@Slf4j
@Service
public class PublicAssetService extends AbstractChunkedFileService {

    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    public PublicAssetService(ObjectStorageService objectStorageService,
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
    public String getPublicUrlFor(String objectName) {
        return minioConfig.getPublicEndpoint() + "/" + getBucketName() + "/" + objectName;
    }

    /**
     * 获取公共存储桶中所有文件的列表。
     *
     * @return 包含文件详细信息的DTO ({@link FileDetailDto}) 列表。
     */
    public List<FileDetailDto> listPublicFiles() {
        List<FileMetadata> metadataList = fileMetadataRepository.findAll(getStorageType());

        return metadataList.stream()
                .filter(Objects::nonNull)
                .map(metadata -> FileDetailDto.builder()
                        .name(metadata.getOriginalFilename())
                        .path(metadata.getObjectName())
                        .size(metadata.getFileSize())
                        .url(getPublicUrlFor(metadata.getObjectName()))
                        .contentType(metadata.getContentType())
                        .build())
                .collect(Collectors.toList());
    }
}