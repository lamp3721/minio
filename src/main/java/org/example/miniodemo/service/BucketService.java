package org.example.miniodemo.service;

public interface BucketService {

    // 检查指定的存储桶是否存在。
    boolean bucketExists(String bucketName) throws Exception;

    // 创建一个新的存储桶。
    void makeBucket(String bucketName) throws Exception;
}
