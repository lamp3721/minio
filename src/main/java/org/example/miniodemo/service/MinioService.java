package org.example.miniodemo.service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.config.MinioConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig; // 注入存储桶配置
    private final MinioConfig minioConfig; // 注入MinIO基础配置
    /**
     * 定义一个临时的目录路径，用于存放所有上传的分片。
     * 这样做的好处是可以方便地对临时文件进行管理和清理。
     */
    private static final String TEMP_CHUNK_PREFIX = "tmp-chunks/";

    /**
     * 获取私有存储桶中所有文件的列表，同时过滤掉临时分片文件。
     *
     * @return 文件信息列表，每个元素是一个包含 'name' 和 'size' 的 Map
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public List<Map<String, Object>> listPrivateFiles() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPrivateFiles()).build());

        return StreamSupport.stream(results.spliterator(), false)
                .map(result -> {
                    try {
                        Item item = result.get();
                        // 通过文件名前缀判断，过滤掉所有未合并的临时分片文件
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
     * 获取公共存储桶中所有文件的列表。
     *
     * @return 文件信息列表，每个元素是一个包含 'name' 和 'url' 的 Map
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public List<Map<String, Object>> listPublicFiles() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPublicAssets()).build());

        // 使用 MinioConfig 来获取基础端点URL
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
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 上传单个文件分片到私有存储桶的临时目录。
     *
     * @param file        前端上传的文件分片 (MultipartFile)
     * @param batchId     本次上传的唯一批次ID，由前端生成
     * @param chunkNumber 当前分片的序号 (从0开始)
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public void uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) throws Exception {
        String objectName = TEMP_CHUNK_PREFIX + batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketConfig.getPrivateFiles()) // 上传到私有桶
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
    }

    /**
     * 将私有存储桶中的指定批次ID的所有分片合并成一个完整的私有文件。
     *
     * @param batchId        本次上传的唯一批次ID
     * @param finalFileName  最终合并后的文件名
     * @throws Exception 如果与 MinIO 通信时发生错误或分片数量不符合要求
     */
    public void mergeChunks(String batchId, String finalFileName) throws Exception {
        // 1. 列出私有桶中指定批次ID下的所有分片对象
        String prefix = TEMP_CHUNK_PREFIX + batchId + "/";
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketConfig.getPrivateFiles()).prefix(prefix).build());

        // 2. 对分片进行排序并构建 ComposeSource 列表
        List<ComposeSource> sourceObjectList = StreamSupport.stream(results.spliterator(), false)
                .map(result -> {
                    try {
                        return new AbstractMap.SimpleEntry<>(result.get().objectName(), result.get());
                    } catch (Exception e) {
                        log.error("Error getting item for composing", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(entry ->
                        Integer.parseInt(entry.getKey().substring(entry.getKey().lastIndexOf("/") + 1))
                ))
                .map(entry -> {
                    try {
                        return ComposeSource.builder()
                                .bucket(bucketConfig.getPrivateFiles())
                                .object(entry.getValue().objectName())
                                .build();
                    } catch (Exception e) {
                        log.error("Error building compose source", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (sourceObjectList.isEmpty() || sourceObjectList.size() > 1024) {
             throw new RuntimeException("分片数量不符合要求，无法合并。当前分片数: " + sourceObjectList.size());
        }

        // 3. 在MinIO服务器端将分片合并到私有桶
        minioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketConfig.getPrivateFiles())
                        .object(finalFileName)
                        .sources(sourceObjectList)
                        .build());

        // 4. 合并成功后，删除已合并的临时分片文件
        List<DeleteObject> objectsToDelete = sourceObjectList.stream()
                .map(s -> new DeleteObject(s.object()))
                .collect(Collectors.toList());

        Iterable<Result<DeleteError>> deleteErrors = minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucketConfig.getPrivateFiles()).objects(objectsToDelete).build());
        
        for (Result<DeleteError> errorResult : deleteErrors) {
            DeleteError error = errorResult.get();
            log.error("删除临时分片失败. Object: {}, Message: {}", error.objectName(), error.message());
        }
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
                            .bucket(bucketConfig.getPublicAssets()) // 上传到公开桶
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
        
        // 构建并返回公开URL
        // 注意: 这个URL的格式依赖于你的MinIO端点和Nginx/网关配置
        // 这里的实现是一个通用模板
        String endpoint = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketConfig.getPublicAssets())
                .object(objectName)
                .expiry(1)
                .build()
        );
        return endpoint.substring(0, endpoint.indexOf('?'));
    }

    /**
     * 获取一个私有文件的预签名下载URL。
     *
     * @param fileName 需要下载的文件名
     * @return 一个有15分钟有效期的、可直接用于下载的URL
     * @throws Exception 如果与 MinIO 通信时发生错误
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
     * 直接下载一个私有文件，返回其数据流。
     *
     * @param fileName 需要下载的文件名
     * @return 文件的输入流
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public InputStream downloadPrivateFile(String fileName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(fileName).build());
    }

    /**
     * 删除一个私有文件。
     *
     * @param fileName 需要删除的文件名
     * @throws Exception 如果与 MinIO 通信时发生错误
     */
    public void deletePrivateFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(fileName).build());
    }

    // --- 存储桶方法 ---

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