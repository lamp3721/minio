package org.example.miniodemo.service;

import org.example.miniodemo.dto.FileDetailDto;

import java.util.List;

public interface PublicAssetService extends AbstractChunkedFile{

    /**
     * 获取公共存储桶中对象的永久访问URL。
     *
     * @param bucketName 存储桶名称。
     * @param filePath   对象名称。
     * @return 对象的可直接访问的URL。
     */
    String getPublicUrl(String bucketName, String filePath);

    /**
     * 获取公共存储桶中对象的永久访问URL。
     *
     * @param filePath   对象名称。
     * @return 对象的可直接访问的URL。
     */
    String getPublicUrl(String filePath);

    // 获取公共存储桶中所有文件的列表。
    List<FileDetailDto> listPublicFiles();
}
