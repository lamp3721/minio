package org.example.miniodemo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.PrivateFileService;
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
public class PrivateFileServiceImpl extends AbstractChunkedFileServiceImpl implements PrivateFileService {

    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    public PrivateFileServiceImpl(ObjectStorageService objectStorageService,
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
     * 列出所有私有存储的文件详情。
     * <p>
     * 从数据库中查询指定存储类型下所有文件的元数据，
     * 并转换为文件详情 DTO 列表返回。
     * <p>
     * 注意：私有文件不会生成访问 URL，因此返回的 DTO 中不包含 URL 信息。
     *
     * @return 包含文件名、路径、大小、内容类型和访问次数的文件详情列表
     */
    @Override
    public List<FileDetailDto> listPrivateFiles() {
        List<FileMetadata> metadataList = fileMetadataRepository.findAll(getStorageType());

        return metadataList.stream()
                .filter(Objects::nonNull)
                .map(metadata -> {
                    try {
                        String url = getPresignedPrivateDownloadUrl(metadata.getFilePath());
                        return FileDetailDto.builder()
                                .name(metadata.getOriginalFilename())
                                .filePath(metadata.getFilePath())
                                .size(metadata.getFileSize())
                                .contentType(metadata.getContentType())
                                .visitCount(metadata.getVisitCount())
                                .url(url)
                                .build();
                    } catch (Exception e) {
                        log.error("获取文件 {} 的预签名URL失败", metadata.getFilePath(), e);
                        return null; // 或者返回一个不带URL的DTO
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * 获取私有文件的预签名下载URL（推荐的下载方式）。
     *
     * @param objectName 文件的对象路径。
     * @return 一个有访问时限的下载URL。
     * @throws Exception 如果生成URL时出错。
     */
    @Override
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
     * 下载私有存储中的文件并异步更新该文件的最后访问时间。
     *
     * <p>方法首先异步触发对文件最后访问时间的更新，保证访问记录及时刷新，
     * 随后调用对象存储服务下载指定路径的文件数据流。
     *
     * @param filePath 文件在存储桶中的相对路径。
     * @return 返回文件内容的输入流。
     * @throws Exception 当下载过程中发生异常时抛出。
     */
    @Override
    public InputStream downloadPrivateFile(String filePath) throws Exception {
        // 异步更新文件最后访问时间，避免阻塞下载操作
        asyncFileService.updateLastAccessedTime(filePath);
        // 从对象存储服务获取文件输入流
        return objectStorageService.download(getBucketName(), filePath);
    }

}