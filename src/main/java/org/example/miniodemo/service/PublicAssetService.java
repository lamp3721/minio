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

/**
 * 专用于处理公共资源（Public Assets）相关操作的服务层。
 * <p>
 * 封装了对MinIO中公开存储桶的操作逻辑，包括列出文件、上传文件和删除文件。
 * 公开资源意味着这些文件可以通过其直接URL被互联网上的任何人访问。
 *
 * @see PublicAssetController
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicAssetService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    /**
     * 获取公共存储桶中所有文件的列表，并为每个文件生成可直接访问的URL。
     *
     * @return 包含文件信息的Map列表。每个Map包含 "name" (对象名) 和 "url" (完整公开URL)。
     * @throws Exception 如果与MinIO服务器通信时发生错误。
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
     * <p>
     * 文件名将由UUID和原始文件名拼接而成，以避免命名冲突。
     *
     * @param file 需要上传的图片文件 ({@link MultipartFile})。
     * @return 文件的永久公开访问URL。
     * @throws Exception 如果与MinIO服务器通信时发生错误或文件上传失败。
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
     * 从公共存储桶中删除一个文件。
     *
     * @param fileName 需要删除的文件的名称（对象名）。
     * @throws Exception 如果与MinIO服务器通信时发生错误或删除操作失败。
     */
    public void deletePublicFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketConfig.getPublicAssets()).object(fileName).build());
    }
} 