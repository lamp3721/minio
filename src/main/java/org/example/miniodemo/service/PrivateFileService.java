package org.example.miniodemo.service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.controller.PrivateFileController;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 专用于处理私有文件（Private Files）相关操作的服务层。
 * <p>
 * 该服务封装了与MinIO私有存储桶交互的所有核心业务逻辑，包括：
 * <ul>
 *     <li>列出已上传的完整文件。</li>
 *     <li>处理文件分片的上传，将每个分片作为临时对象存入MinIO。</li>
 *     <li>将所有上传成功的分片合并成一个最终的完整文件。</li>
 *     <li>为文件生成预签名的下载URL。</li>
 *     <li>提供通过服务器代理下载文件流的功能。</li>
 *     <li>删除文件。</li>
 * </ul>
 * 这是整个私有文件管理功能的核心。
 *
 * @see PrivateFileController
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateFileService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig;
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final String ORIGINAL_FILENAME_META_KEY = "X-Amz-Meta-Original-Filename";

    /**
     * 检查具有特定哈希值的文件是否已存在于当天的路径下。
     *
     * @param fileHash 文件的MD5哈希值。
     * @param fileName 文件的原始名称。
     * @return 如果文件存在，则为true；否则为false。
     */
    public boolean checkFileExists(String fileHash, String fileName) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String objectName = String.join("/", year, month, day, fileHash, fileName);

        log.info("【秒传检查 - 私有库】开始检查文件是否存在，对象路径: {}", objectName);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketConfig.getPrivateFiles())
                    .object(objectName)
                    .build());
            log.info("【秒传检查 - 私有库】文件已存在 (对象路径: {})。将触发秒传。", objectName);
            return true;
        } catch (Exception e) {
            log.info("【秒传检查 - 私有库】文件不存在 (对象路径: {})。将执行新上传。", objectName);
            return false;
        }
    }

    /**
     * 列出私有存储桶中所有最终合并完成的文件。
     * <p>
     * 此方法会过滤掉分片上传过程中产生的临时文件。
     * 它会从文件的完整对象路径中解析出原始文件名。
     *
     * @return 文件信息列表，每个Map包含 "name" (原始文件名) 和 "size"。
     * @throws MinioException 如果发生MinIO相关错误。
     */
    public List<Map<String, Object>> listPrivateFiles() throws MinioException {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketConfig.getPrivateFiles())
                    .recursive(true)
                    .build());

            return StreamSupport.stream(results.spliterator(), false)
                    .map(itemResult -> {
                        try {
                            return itemResult.get();
                        } catch (Exception e) {
                            log.error("获取对象信息失败", e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    // 使用正则表达式匹配 YYYY/MM/DD/hash/filename 格式的路径
                    // 这样可以精确地只包含最终文件，并排除临时分片（如 batchId/0）和虚拟目录
                    .filter(item -> item.objectName().matches("^\\d{4}/\\d{2}/\\d{2}/.+/.+"))
                    .map(item -> {
                        try {
                            String objectName = item.objectName();
                            // 从对象路径中提取原始文件名
                            String originalName = objectName.substring(objectName.lastIndexOf('/') + 1);

                            Map<String, Object> fileInfo = new HashMap<>();
                            fileInfo.put("name", originalName); // 返回原始文件名
                            fileInfo.put("hashName", objectName); // 返回完整的对象路径
                            fileInfo.put("size", item.size());
                            return fileInfo;
                        } catch (Exception e) {
                            log.error("解析对象 '{}' 信息失败", item.objectName(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("列出私有文件失败", e);
            throw new MinioException("无法列出文件");
        }
    }

    /**
     * 上传一个文件分片到MinIO。
     * <p>
     * 分片会被存储为一个临时对象，其对象名遵循特定格式：
     * {@code [batchId]/[fileName].chunk_[chunkNumber]}
     * 例如："abc-123/my-video.mp4.chunk_0"
     * 这种命名方式便于后续的合并操作识别和排序。
     *
     * @param file        文件分片的二进制数据。
     * @param batchId     唯一标识此次上传任务的批次ID。
     * @param chunkNumber 当前分片的序号（从0开始）。
     * @throws Exception 如果上传过程中发生IO错误或与MinIO通信失败。
     */
    public void uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) throws Exception {
        String objectName = batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketConfig.getPrivateFiles())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .build()
            );
        }
    }

    /**
     * 合并所有分片。
     *
     * @param batchId          批次ID。
     * @param originalFileName 原始文件名。
     * @param fileHash         文件的MD5哈希值，将作为最终的对象名。
     * @throws MinioException 如果合并过程中发生错误。
     */
    public void mergeChunks(String batchId, String originalFileName, String fileHash) throws Exception {
        try {
            // 1. 列出该批次的所有分片
            List<ComposeSource> sources = StreamSupport.stream(
                            minioClient.listObjects(ListObjectsArgs.builder()
                                    .bucket(bucketConfig.getPrivateFiles())
                                    .prefix(batchId + "/") // 分片存储在以batchId为名的虚拟目录下
                                    .recursive(true)
                                    .build()).spliterator(), false)
                    .map(itemResult -> {
                        try {
                            Item item = itemResult.get();
                            return ComposeSource.builder()
                                    .bucket(bucketConfig.getPrivateFiles())
                                    .object(item.objectName())
                                    .build();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sorted(Comparator.comparing(s -> Integer.valueOf(s.object().substring(s.object().lastIndexOf('/') + 1))))
                    .collect(Collectors.toList());

            if (sources.isEmpty()) {
                throw new MinioException("找不到任何分片进行合并，批次ID: " + batchId);
            }

            // 2. 构建基于日期的最终对象路径
            LocalDate now = LocalDate.now();
            String year = String.valueOf(now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            String day = String.format("%02d", now.getDayOfMonth());
            String finalObjectName = String.join("/", year, month, day, fileHash, originalFileName);

            // 3. 将分片合并成一个新对象
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucketConfig.getPrivateFiles())
                    .object(finalObjectName)
                    .sources(sources)
                    .build());

            log.info("【文件合并 - 私有库】文件合并成功。最终对象路径: '{}'。", finalObjectName);
            deleteTemporaryChunks(batchId);

        } catch (Exception e) {
            log.error("合并文件失败，批次ID: {}", batchId, e);
            throw new MinioException("合并文件失败");
        }
    }

    /**
     * 获取私有文件的预签名下载URL。
     *
     * @param objectName 文件的对象名（即其 fileHash）。
     * @return 临时的下载URL。
     * @throws MinioException 如果生成URL时出错。
     */
    public String getPresignedPrivateDownloadUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketConfig.getPrivateFiles())
                        .object(objectName)
                        .expiry(15, TimeUnit.MINUTES)
                        .build()
        );
    }

    /**
     * 通过服务器代理的方式，获取私有文件的输入流。
     * <p>
     * 此方法直接从MinIO获取指定对象的输入流，上层调用者（如Controller）可以将此流
     * 直接转发给客户端，实现文件下载。
     *
     * @param objectName 需要下载的文件的对象名。
     * @return 包含文件数据的 {@link InputStream}。
     * @throws Exception 如果获取对象时发生错误。
     */
    public InputStream downloadPrivateFile(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(objectName).build());
    }

    /**
     * 删除一个私有文件。
     *
     * @param objectName 要删除的文件的对象名（即其 fileHash）。
     * @throws MinioException 如果删除操作失败。
     */
    public void deletePrivateFile(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketConfig.getPrivateFiles())
                .object(objectName)
                .build());
    }

    private void deleteTemporaryChunks(String batchId) {
        try {
            List<DeleteObject> toDelete = StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder()
                            .bucket(bucketConfig.getPrivateFiles())
                            .prefix(batchId + "/")
                            .recursive(true)
                            .build()).spliterator(), false)
                    .map(itemResult -> {
                        try {
                            return new DeleteObject(itemResult.get().objectName());
                        } catch (Exception e) {
                           return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!toDelete.isEmpty()) {
                Iterable<Result<DeleteError>> results = minioClient.removeObjects(RemoveObjectsArgs.builder()
                        .bucket(bucketConfig.getPrivateFiles())
                        .objects(toDelete)
                        .build());
                for (Result<DeleteError> result : results) {
                    DeleteError error = result.get();
                    log.error("删除临时分片失败. Object: {}, Message: {}", error.objectName(), error.message());
                }
            }
        } catch (Exception e) {
            log.error("删除临时分片失败, batchId: {}", batchId, e);
        }
    }
} 