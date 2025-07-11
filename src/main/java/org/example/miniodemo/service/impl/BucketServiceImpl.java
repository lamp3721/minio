package org.example.miniodemo.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.example.miniodemo.service.BucketService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 处理存储桶（Bucket）相关操作的服务层。
 * <p>
 * 封装了创建存储桶和检查存储桶是否存在的底层操作。
 * 此服务直接与MinIO客户端交互，为上层应用提供简化的接口。
 *
 * @see org.example.miniodemo.controller.BucketController
 */
@Service
public class BucketServiceImpl implements BucketService {

    private final MinioClient minioClient;

    public BucketServiceImpl(@Qualifier("internalMinioClient") MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 检查指定的存储桶是否存在。
     *
     * @param bucketName 要检查的存储桶名称。
     * @return 如果存储桶存在，则返回 {@code true}；否则返回 {@code false}。
     * @throws Exception 如果与MinIO服务器通信时发生错误。
     */
    @Override
    public boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建一个新的存储桶。
     *
     * @param bucketName 要创建的存储桶的名称，必须遵循MinIO的命名规范。
     * @throws Exception 如果与MinIO服务器通信时发生错误，或存储桶已存在。
     */
    @Override
    public void makeBucket(String bucketName) throws Exception {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }
} 