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

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateFileService {

    private final MinioClient minioClient;
    private final MinioBucketConfig bucketConfig;
    private static final String TEMP_CHUNK_PREFIX = "tmp-chunks/";

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

    public InputStream downloadPrivateFile(String fileName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(fileName).build());
    }

    public void deletePrivateFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketConfig.getPrivateFiles()).object(fileName).build());
    }
} 