package org.example.miniodemo.service;

import org.example.miniodemo.dto.FileDetailDto;

import java.util.List;

public interface PublicAssetService extends AbstractChunkedFile{

    // 为给定的对象名生成公开访问URL
    String getPublicUrlFor(String objectName);

    // 获取公共存储桶中所有文件的列表。
    List<FileDetailDto> listPublicFiles();
}
