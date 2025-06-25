package org.example.miniodemo.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BucketService {

    private final MinioClient minioClient;

    /**
     * 检查一个存储桶是否存在。
     *
     * @param bucketName 存储桶名称
     * @return 如果存在则返回 true, 否则返回 false
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建一个新的存储桶。
     *
     * @param bucketName 需要创建的存储桶名称
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public void makeBucket(String bucketName) throws Exception {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }
} 