package org.example.miniodemo.service.storage;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.domain.StorageObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * ObjectStorageService 的 MinIO 实现。
 * <p>
 * 封装了所有与 MinIO 交互的具体逻辑，将 MinIO 的 SDK 调用与业务逻辑完全隔离。
 */
@Slf4j
@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;

    public MinioObjectStorageService(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
    }

    @Override
    public void upload(String bucketName, String objectName, InputStream stream, long size, String contentType) throws Exception {
        internalMinioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    @Override
    public void compose(String bucketName, List<String> sourceObjectNames, String targetObjectName) throws Exception {
        List<ComposeSource> sources = sourceObjectNames.stream()
                .map(obj -> ComposeSource.builder().bucket(bucketName).object(obj).build())
                .collect(Collectors.toList());

        internalMinioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketName)
                        .object(targetObjectName)
                        .sources(sources)
                        .build()
        );
    }

    @Override
    public List<StorageObject> listObjects(String bucketName, String prefix, boolean recursive) throws Exception {
        Iterable<Result<Item>> results = internalMinioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(recursive)
                        .build()
        );

        return StreamSupport.stream(results.spliterator(), false)
                .map(itemResult -> {
                    try {
                        Item item = itemResult.get();
                        return StorageObject.builder()
                                .filePath(item.objectName())
                                .size(item.size())
                                .lastModified(item.lastModified())
                                .build();
                    } catch (Exception e) {
                        log.error("在列举对象时，获取对象 '{}' 信息失败", itemResult.toString(), e);
                        return null;
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public InputStream download(String bucketName, String objectName) throws Exception {
        return internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    @Override
    public void delete(String bucketName, String objectName) throws Exception {
        internalMinioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    @Override
    public void delete(String bucketName, List<String> objectNames) throws Exception {
        List<DeleteObject> toDelete = objectNames.stream()
                .map(DeleteObject::new)
                .collect(Collectors.toList());

        Iterable<Result<DeleteError>> errors = internalMinioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(bucketName)
                        .objects(toDelete)
                        .build()
        );

        // 检查并记录删除错误
        for (Result<DeleteError> errorResult : errors) {
            DeleteError error = errorResult.get();
            log.error("批量删除对象时发生错误. Object: {}, Message: {}", error.objectName(), error.message());
        }
    }

    @Override
    public String getPresignedDownloadUrl(String bucketName, String objectName, int duration, TimeUnit unit) throws Exception {
        // 预签名URL必须使用面向公网的客户端生成
        return publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(duration, unit)
                        .build()
        );
    }
} 