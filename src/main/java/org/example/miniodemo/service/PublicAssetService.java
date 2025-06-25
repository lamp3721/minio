package org.example.miniodemo.service;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.config.MinioConfig;
import org.example.miniodemo.controller.PublicAssetController;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Objects;

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
    private static final String ORIGINAL_FILENAME_META_KEY = "X-Amz-Meta-Original-Filename";

    /**
     * 检查文件是否存在。
     *
     * @param fileHash 文件的哈希值。
     * @return 如果文件存在，则返回true；否则返回false。
     */
    public boolean checkFileExists(String fileHash) {
        log.info("【秒传检查 - 公共库】开始检查文件是否存在，哈希: {}", fileHash);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketConfig.getPublicAssets())
                    .object(fileHash)
                    .build());
            log.info("【秒传检查 - 公共库】文件已存在 (哈希: {})。将触发秒传。", fileHash);
            return true;
        } catch (Exception e) {
            log.info("【秒传检查 - 公共库】文件不存在 (哈希: {})。将执行新上传。", fileHash);
            return false;
        }
    }

    /**
     * 获取公共存储桶中所有文件的列表，并为每个文件生成可直接访问的URL。
     *
     * @return 包含文件信息的Map列表。每个Map包含 "name" (对象名) 和 "url" (完整公开URL)。
     * @throws Exception 如果与MinIO服务器通信时发生错误。
     */
    public List<Map<String, Object>> listPublicFiles() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPublicAssets()).recursive(true).build()
        );

        String baseUrl = minioConfig.getEndpoint() + "/" + bucketConfig.getPublicAssets() + "/";

        return StreamSupport.stream(results.spliterator(), false)
                .map(itemResult -> {
                    try {
                        Item item = itemResult.get();
                        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                                .bucket(bucketConfig.getPublicAssets())
                                .object(item.objectName())
                                .build());

                        String originalName = stat.userMetadata().getOrDefault(
                                ORIGINAL_FILENAME_META_KEY.substring("X-Amz-Meta-".length()).toLowerCase(),
                                item.objectName()
                        );

                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", originalName);
                        fileInfo.put("hashName", item.objectName());
                        fileInfo.put("url", baseUrl + item.objectName());

                        return fileInfo;
                    } catch (Exception e) {
                        log.error("获取公共文件 '{}' 信息失败", itemResult.toString(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 上传一个公开的图片文件，并返回其永久公开URL。
     * <p>
     * 文件名将由UUID和原始文件名拼接而成，以避免命名冲突。
     *
     * @param file 需要上传的图片文件 ({@link MultipartFile})。
     * @param fileHash 文件的哈希值。
     * @return 文件的永久公开访问URL。
     * @throws Exception 如果与MinIO服务器通信时发生错误或文件上传失败。
     */
    public String uploadPublicImage(MultipartFile file, String fileHash) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketConfig.getPublicAssets())
                            .object(fileHash)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .headers(Map.of(ORIGINAL_FILENAME_META_KEY, file.getOriginalFilename()))
                            .build()
            );
            log.info("【文件上传 - 公共库】文件上传成功。对象名 (哈希): '{}'，原始文件名: '{}'。", fileHash, file.getOriginalFilename());
        }

        return minioConfig.getEndpoint() + "/" + bucketConfig.getPublicAssets() + "/" + fileHash;
    }

    /**
     * 从公共存储桶中删除一个文件。
     *
     * @param objectName 需要删除的文件的名称（对象名）。
     * @throws Exception 如果与MinIO服务器通信时发生错误或删除操作失败。
     */
    public void deletePublicFile(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketConfig.getPublicAssets())
                        .object(objectName)
                        .build()
        );
    }
} 