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
import org.example.miniodemo.dto.FileDetailDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class PublicAssetService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig;
    private final MinioConfig minioConfig;

    public PublicAssetService(
            @Qualifier("internalMinioClient") MinioClient minioClient,
            MinioBucketConfig bucketConfig,
            MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.bucketConfig = bucketConfig;
        this.minioConfig = minioConfig;
    }

    /**
     * 检查文件是否存在。
     * <p>
     * 新的逻辑会根据当前日期、文件哈希和原始文件名构造完整的对象路径进行检查。
     * 这意味着"秒传"仅对当天上传的、哈希和文件名都相同的文件有效。
     *
     * @param fileHash 文件的哈希值。
     * @param fileName 文件的原始名称。
     * @return 如果文件存在，则返回true；否则返回false。
     */
    public boolean checkFileExists(String fileHash, String fileName) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String objectName = String.join("/", year, month, day, fileHash, fileName);

        log.info("【秒传检查 - 公共库】开始检查文件是否存在，对象路径: {}", objectName);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketConfig.getPublicAssets())
                    .object(objectName)
                    .build());
            log.info("【秒传检查 - 公共库】文件已存在 (对象路径: {})。将触发秒传。", objectName);
            return true;
        } catch (Exception e) {
            log.info("【秒传检查 - 公共库】文件不存在 (对象路径: {})。将执行新上传。", objectName);
            return false;
        }
    }

    /**
     * 获取公共存储桶中所有文件的列表，并为每个文件生成可直接访问的URL。
     *
     * @return 包含文件信息的DTO列表。
     * @throws Exception 如果与MinIO服务器通信时发生错误。
     */
    public List<FileDetailDto> listPublicFiles() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPublicAssets()).recursive(true).build()
        );

        String baseUrl = minioConfig.getPublicEndpoint() + "/" + bucketConfig.getPublicAssets() + "/";

        return StreamSupport.stream(results.spliterator(), false)
                .map(itemResult -> {
                    try {
                        Item item = itemResult.get();
                        String objectName = item.objectName();
                        
                        // 从对象路径中提取原始文件名
                        String originalName = objectName.substring(objectName.lastIndexOf('/') + 1);

                        return FileDetailDto.builder()
                                .name(originalName)
                                .path(objectName)
                                .size(item.size())
                                .url(baseUrl + objectName)
                                .build();
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
     * 文件将存储在基于日期的路径下：/年/月/日/文件哈希/原始文件名
     *
     * @param file 需要上传的图片文件 ({@link MultipartFile})。
     * @param fileHash 文件的哈希值。
     * @return 文件的永久公开访问URL。
     * @throws Exception 如果与MinIO服务器通信时发生错误或文件上传失败。
     */
    public String uploadPublicImage(MultipartFile file, String fileHash) throws Exception {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String originalFileName = file.getOriginalFilename();

        String objectName = String.join("/", year, month, day, fileHash, originalFileName);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketConfig.getPublicAssets())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("【文件上传 - 公共库】文件上传成功。对象路径: '{}'。", objectName);
        }

        return minioConfig.getPublicEndpoint() + "/" + bucketConfig.getPublicAssets() + "/" + objectName;
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