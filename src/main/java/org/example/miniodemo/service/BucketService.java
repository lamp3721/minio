package org.example.miniodemo.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 专用于处理存储桶（Bucket）相关操作的服务层。
 * <p>
 * 封装了对MinIO客户端的直接调用，提供了如检查存储桶是否存在、创建存储桶等原子操作。
 * 被 {@link BucketController} 调用。
 */
@Service
@RequiredArgsConstructor
public class BucketService {

    private final MinioClient minioClient;

    /**
     * 检查一个存储桶是否存在。
     *
     * @param bucketName 存储桶名称。不能为空或仅包含空格。
     * @return 如果存储桶存在，则返回 {@code true}；否则返回 {@code false}。
     * @throws Exception 如果与MinIO服务器通信时发生错误，例如网络问题或认证失败。
     */
    public boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建一个新的存储桶。
     *
     * @param bucketName 需要创建的存储桶的名称。不能为空或仅包含空格，且必须遵循MinIO的命名规范。
     * @throws Exception 如果与MinIO服务器通信时发生错误，或存储桶名称无效，或存储桶已存在。
     */
    public void makeBucket(String bucketName) throws Exception {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }
} 