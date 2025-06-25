package org.example.miniodemo.service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
    private static final String TEMP_CHUNK_PREFIX = "tmp-chunks/";

    /**
     * 列出私有存储桶中的所有最终文件。
     * <p>
     * 此方法会智能地过滤掉所有以 {@code .chunk_} 为后缀的临时分片文件，
     * 只返回用户上传的、已合并的完整文件。
     *
     * @return 包含文件信息的Map列表，每个Map包含 "name" (对象名) 和 "size" (文件大小，单位字节)。
     * @throws Exception 如果与MinIO通信时发生错误。
     */
    public List<Map<String, Object>> listPrivateFiles() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPrivateFiles()).build());

        return StreamSupport.stream(results.spliterator(), false)
                .map(result -> {
                    try {
                        Item item = result.get();
                        if (item.objectName().startsWith(TEMP_CHUNK_PREFIX)) {
                            return null;
                        }
                        Map<String, Object> fileData = new HashMap<>();
                        fileData.put("name", item.objectName());
                        fileData.put("size", item.size());
                        return fileData;
                    } catch (Exception e) {
                        log.error("Error getting item from MinIO", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
        String objectName = TEMP_CHUNK_PREFIX + batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketConfig.getPrivateFiles())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
    }

    /**
     * 将指定批次ID的所有分片合并成一个最终文件。
     * <p>
     * 此方法执行以下步骤：
     * <ol>
     *     <li>根据批次ID列出所有相关的分片临时对象。</li>
     *     <li>对分片按其序号进行正确排序，以确保文件内容的正确性。</li>
     *     <li>使用MinIO的 {@code composeObject} API将所有分片在服务器端高效合并。</li>
     *     <li>合并成功后，删除所有临时的分片对象。</li>
     * </ol>
     *
     * @param batchId  需要合并的分片所属的批次ID。
     * @param fileName 合并后最终生成的文件名。
     * @throws Exception 如果列出、合并或删除分片时发生错误，或分片数量超过MinIO的合并限制。
     */
    public void mergeChunks(String batchId, String finalFileName) throws Exception {
        String prefix = TEMP_CHUNK_PREFIX + batchId + "/";
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPrivateFiles()).prefix(prefix).build());

        List<ComposeSource> sourceObjectList = StreamSupport.stream(results.spliterator(), false)
                .sorted(Comparator.comparingInt(itemResult -> {
                    try {
                        String key = itemResult.get().objectName();
                        return Integer.parseInt(key.substring(key.lastIndexOf("/") + 1));
                    } catch (Exception e) {
                        return 0;
                    }
                }))
                .map(itemResult -> {
                    try {
                        return ComposeSource.builder()
                                .bucket(bucketConfig.getPrivateFiles())
                                .object(itemResult.get().objectName())
                                .build();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (sourceObjectList.isEmpty()) {
            throw new RuntimeException("分片数量为空，无法合并。");
        }

        minioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketConfig.getPrivateFiles())
                        .object(finalFileName)
                        .sources(sourceObjectList)
                        .build());

        List<DeleteObject> objectsToDelete = sourceObjectList.stream()
                .map(s -> new DeleteObject(s.object()))
                .collect(Collectors.toList());
        
        handleDeleteErrors(minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucketConfig.getPrivateFiles()).objects(objectsToDelete).build()));
    }

    private void handleDeleteErrors(Iterable<Result<DeleteError>> deleteErrors) {
        for (Result<DeleteError> errorResult : deleteErrors) {
            try {
                DeleteError error = errorResult.get();
                log.error("删除临时分片失败. Object: {}, Message: {}", error.objectName(), error.message());
            } catch (Exception e) {
                log.error("获取删除错误信息时异常", e);
            }
        }
    }

    /**
     * 生成一个私有文件的预签名下载URL。
     * <p>
     * 客户端可以使用此URL在限定时间内（当前设置为15分钟）直接从MinIO下载文件，
     * 无需通过本应用服务器。
     *
     * @param fileName 需要生成URL的文件的对象名。
     * @return 一个具有时效性的可供下载的URL字符串。
     * @throws Exception 如果生成URL时发生错误。
     */
    public String getPresignedPrivateDownloadUrl(String fileName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketConfig.getPrivateFiles())
                        .object(fileName)
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
     * @param fileName 需要下载的文件的对象名。
     * @return 包含文件数据的 {@link InputStream}。
     * @throws Exception 如果获取对象时发生错误。
     */
    public InputStream downloadPrivateFile(String fileName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(fileName).build());
    }

    /**
     * 删除一个私有文件。
     *
     * @param fileName 需要删除的文件的对象名。
     * @throws Exception 如果删除对象时发生错误。
     */
    public void deletePrivateFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(fileName).build());
    }
} 