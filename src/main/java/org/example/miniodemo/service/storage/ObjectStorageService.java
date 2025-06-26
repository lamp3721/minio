package org.example.miniodemo.service.storage;

import org.example.miniodemo.domain.StorageObject;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 通用对象存储服务接口.
 * <p>
 * 定义了所有与对象存储交互的标准化操作，实现了业务逻辑与具体存储方案（如MinIO, S3等）的解耦。
 * 所有方法都接受 bucketName 作为参数，使得此服务可以操作多个存储桶。
 */
public interface ObjectStorageService {

    /**
     * 上传一个对象。
     *
     * @param bucketName  存储桶名称。
     * @param objectName  对象的完整路径和名称。
     * @param stream      文件的输入流。
     * @param size        文件大小。
     * @param contentType 文件的MIME类型。
     * @throws Exception 如果上传失败。
     */
    void upload(String bucketName, String objectName, InputStream stream, long size, String contentType) throws Exception;

    /**
     * 将多个源对象合并成一个目标对象。主要用于分片上传的合并步骤。
     *
     * @param bucketName        存储桶名称。
     * @param sourceObjectNames 有序的源对象（分片）列表。
     * @param targetObjectName  最终合并后的对象名称。
     * @throws Exception 如果合并失败。
     */
    void compose(String bucketName, List<String> sourceObjectNames, String targetObjectName) throws Exception;

    /**
     * 列出指定存储桶和前缀下的所有对象。
     *
     * @param bucketName 存储桶名称。
     * @param prefix     对象名称前缀，用于筛选。
     * @param recursive  是否递归查找所有子目录。
     * @return 存储对象信息列表。
     * @throws Exception 如果列举失败。
     */
    List<StorageObject> listObjects(String bucketName, String prefix, boolean recursive) throws Exception;

    /**
     * 获取一个对象的下载输入流。
     *
     * @param bucketName 存储桶名称。
     * @param objectName 对象名称。
     * @return 文件的输入流。
     * @throws Exception 如果获取失败。
     */
    InputStream download(String bucketName, String objectName) throws Exception;

    /**
     * 删除单个对象。
     *
     * @param bucketName 存储桶名称。
     * @param objectName 对象名称。
     * @throws Exception 如果删除失败。
     */
    void delete(String bucketName, String objectName) throws Exception;

    /**
     * 批量删除多个对象。
     *
     * @param bucketName  存储桶名称。
     * @param objectNames 要删除的对象名称列表。
     * @throws Exception 如果批量删除操作中出现错误。
     */
    void delete(String bucketName, List<String> objectNames) throws Exception;

    /**
     * 为私有对象生成一个带签名的、有时效的下载URL。
     *
     * @param bucketName 存储桶名称。
     * @param objectName 对象名称。
     * @param duration   URL的有效时长。
     * @param unit       时长单位。
     * @return 预签名的下载URL。
     * @throws Exception 如果生成URL失败。
     */
    String getPresignedDownloadUrl(String bucketName, String objectName, int duration, TimeUnit unit) throws Exception;
} 