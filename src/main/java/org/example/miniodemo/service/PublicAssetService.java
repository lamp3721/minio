package org.example.miniodemo.service;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.PutObjectArgs;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.config.MinioConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicAssetService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    /**
     * 获取公共存储桶中所有文件的列表。
     *
     * @return 文件信息列表，每个元素是一个包含 'name' 和 'url' 的 Map
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public List<Map<String, Object>> listPublicFiles() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPublicAssets()).build());

        String baseUrl = minioConfig.getEndpoint() + "/" + bucketConfig.getPublicAssets() + "/";

        return StreamSupport.stream(results.spliterator(), false)
                .map(result -> {
                    try {
                        Item item = result.get();
                        Map<String, Object> fileData = new HashMap<>();
                        fileData.put("name", item.objectName());
                        fileData.put("url", baseUrl + item.objectName());
                        return fileData;
                    } catch (Exception e) {
                        log.error("Error getting item from MinIO for public list", e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 上传一个公开的图片文件，并返回其永久公开URL。
     *
     * @param file 需要上传的图片文件
     * @return 永久可访问的公开URL
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public String uploadPublicImage(MultipartFile file) throws Exception {
        String objectName = UUID.randomUUID() + "-" + file.getOriginalFilename();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketConfig.getPublicAssets())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
        return minioConfig.getEndpoint() + "/" + bucketConfig.getPublicAssets() + "/" + objectName;
    }

    /**
     * 删除一个公共文件。
     *
     * @param fileName 需要删除的文件名
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public void deletePublicFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketConfig.getPublicAssets()).object(fileName).build());
    }
} 