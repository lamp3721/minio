package org.example.miniodemo.service.storage;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioConfig;
import org.example.miniodemo.domain.StorageObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * MinIO 实现的对象存储服务。
 *
 * 该服务封装了所有与 MinIO 交互的具体逻辑，
 * 将 MinIO SDK 的调用与业务逻辑隔离，便于维护和替换存储实现。
 */
@Slf4j
@Service
public class MinioObjectStorageService implements ObjectStorageService {

    /**
     * 内部使用的 MinIO 客户端，通常用于私有访问。
     */
    private final MinioClient internalMinioClient;

    /**
     * 公网访问的 MinIO 客户端，主要用于生成对外公开的访问链接（预签名 URL）。
     */
    private final MinioClient publicMinioClient;
    private final MinioConfig minioConfig;

    /**
     * 构造方法，注入两个不同配置的 MinIO 客户端实例。
     * @param internalMinioClient 内部访问客户端
     * @param publicMinioClient 公网访问客户端
     */
    public MinioObjectStorageService(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient,
            MinioConfig minioConfig) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.minioConfig = minioConfig;
    }

    /**
     * 上传文件到指定存储桶。
     * @param bucketName 存储桶名称
     * @param filePath 对象路径（文件名）
     * @param stream 文件内容输入流
     * @param size 文件大小
     * @param contentType 文件的 MIME 类型
     * @throws Exception 上传失败时抛出异常
     */
    @Override
    public void upload(String bucketName, String filePath, InputStream stream, long size, String contentType) throws Exception {
        internalMinioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    /**
     * 合成（拼接）多个源对象为一个新的目标对象。
     * @param bucketName 存储桶名称
     * @param sourceObjectNames 需要合成的源对象列表   cf17ce6f77e88fefd44ccb2f0e751967/0 &nbsp;&nbsp;   cf17ce6f77e88fefd44ccb2f0e751967/1
     * @param targetObjectName 合成后生成的目标对象名称
     * @throws Exception 合成失败时抛出异常
     */
    @Override
    public void compose(String bucketName, List<String> sourceObjectNames, String targetObjectName) throws Exception {
        // 将源对象名称转换为 ComposeSource 对象
        List<ComposeSource> sources = sourceObjectNames.stream()
                .map(obj -> ComposeSource.builder().bucket(bucketName).object(obj).build())
                .collect(Collectors.toList());



        // 调用 MinIO SDK 的 composeObject 实现对象合成
        internalMinioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketName)
                        .object(targetObjectName)
                        .sources(sources)
                        .build()
        );
    }

    /**
     * 列出指定存储桶下符合条件的对象列表。
     * @param bucketName 存储桶名称
     * @param prefix 对象名前缀，用于过滤
     * @param recursive 是否递归列出所有子目录中的对象
     * @return 符合条件的对象列表，封装为 StorageObject 实体
     * @throws Exception 列举失败时抛出异常
     */
    @Override
    public List<StorageObject> listObjects(String bucketName, String prefix, boolean recursive) throws Exception {
        Iterable<Result<Item>> results = internalMinioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(recursive)
                        .build()
        );

        // 转换结果为 StorageObject 列表，异常时记录错误并返回 null
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
                .filter(obj -> obj != null) // 过滤掉转换失败的对象
                .collect(Collectors.toList());
    }

    /**
     * 下载指定存储桶中的对象。
     * @param bucketName 存储桶名称
     * @param filePath 对象路径（文件名）
     * @return 文件内容的输入流
     * @throws Exception 下载失败时抛出异常
     */
    @Override
    public InputStream download(String bucketName, String filePath) throws Exception {
        return internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .build()
        );
    }

    /**
     * 删除指定存储桶中的单个对象。
     * @param bucketName 存储桶名称
     * @param filePath 要删除的对象路径
     * @throws Exception 删除失败时抛出异常
     */
    @Override
    public void delete(String bucketName, String filePath) throws Exception {
        internalMinioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .build()
        );
    }

    /**
     * 批量删除存储桶中的多个对象。
     * @param bucketName 存储桶名称
     * @param filePaths 要删除的对象路径列表
     * @throws Exception 删除过程中出现错误时抛出异常
     */
    @Override
    public void delete(String bucketName, List<String> filePaths) throws Exception {
        // 构建删除对象列表
        List<DeleteObject> toDelete = filePaths.stream()
                .map(DeleteObject::new)
                .collect(Collectors.toList());

        // 批量删除操作
        Iterable<Result<DeleteError>> errors = internalMinioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(bucketName)
                        .objects(toDelete)
                        .build()
        );

        // 遍历并记录删除过程中的错误信息
        for (Result<DeleteError> errorResult : errors) {
            DeleteError error = errorResult.get();
            log.error("批量删除对象时发生错误。对象: {}, 消息: {}", error.objectName(), error.message());
        }
    }

    /**
     * 生成指定对象的预签名下载 URL，供公网访问使用。
     * @param bucketName 存储桶名称
     * @param filePath 对象路径
     * @param duration URL 有效时长
     * @param unit 有效时长单位
     * @return 预签名 URL 字符串
     * @throws Exception 生成失败时抛出异常
     */
    @Override
    public String getPresignedDownloadUrl(String bucketName, String filePath, int duration, TimeUnit unit) throws Exception {
        // 预签名 URL 必须用面向公网的客户端生成，以保证访问权限正确
        return publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(filePath)
                        .expiry(duration, unit)
                        .build()
        );
    }
}
