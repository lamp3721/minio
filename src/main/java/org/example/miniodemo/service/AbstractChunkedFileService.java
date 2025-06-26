package org.example.miniodemo.service;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 抽象分片文件服务，封装了分片上传、合并和秒传的通用逻辑。
 */
@Slf4j
public abstract class AbstractChunkedFileService {

    protected final ObjectStorageService objectStorageService;
    protected final FileMetadataRepository fileMetadataRepository;
    protected final AsyncFileService asyncFileService;

    public AbstractChunkedFileService(ObjectStorageService objectStorageService,
                                      FileMetadataRepository fileMetadataRepository,
                                      AsyncFileService asyncFileService) {
        this.objectStorageService = objectStorageService;
        this.fileMetadataRepository = fileMetadataRepository;
        this.asyncFileService = asyncFileService;
    }

    // --- 抽象方法，由子类实现 ---

    /**
     * 获取当前服务操作的存储桶名称。
     */
    protected abstract String getBucketName();

    /**
     * 获取当前服务的存储类型（PUBLIC 或 PRIVATE）。
     */
    protected abstract StorageType getStorageType();



    // --- 通用公共方法 ---

    /**
     * 合并成功后，执行特定的异步清理任务。
     * @param batchId 合并批次ID
     * @param objectNames 要删除的分片对象路径列表。
     * @param bucketName 存储桶名称
     */
    protected void triggerAsyncChunkCleanup(String batchId, List<String> objectNames,String bucketName){
        asyncFileService.deleteTemporaryChunks(batchId, objectNames,bucketName);
    }

    /**
     * 检查文件是否存在
     * @param fileHash 文件哈希值
     * @return 文件元数据
     */
    public Optional<FileMetadata> checkFileExists(String fileHash) {
        return fileMetadataRepository.findByHash(fileHash, getStorageType());
    }

    /**
     * 上传一个分片
     * @param file  文件
     * @param batchId 批次ID
     * @param chunkNumber 分片序号
     * @throws Exception 如果上传分片时发生错误。
     */
    public void uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) throws Exception {
        String objectName = batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    getBucketName(),
                    objectName,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        }
    }

    /**
     * 获取已上传的分片列表。
     * @param batchId 分片上传批次ID。
     * @return 已上传的分片列表。
     * @throws Exception 如果查询时出错。
     */
    public List<Integer> getUploadedChunkNumbers(String batchId) throws Exception {
        List<StorageObject> chunks = objectStorageService.listObjects(
                getBucketName(),
                batchId + "/",
                false
        );
        return chunks.stream()
                .map(chunk -> {
                    try {
                        return Integer.parseInt(chunk.getObjectName().substring(chunk.getObjectName().lastIndexOf('/') + 1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 合并分片文件。
     * @param batchId 分片上传批次ID
     * @param originalFileName 原始文件名
     * @param fileHash 文件哈希值
     * @param contentType 文件内容类型
     * @param fileSize 文件大小
     * @return 合并后的文件元数据
     * @throws Exception 如果列出、合并或删除分片时发生错误，或分片数量超过MinIO的合并限制。
     */
    @Transactional
    public FileMetadata mergeChunks(String batchId, String originalFileName, String fileHash, String contentType, Long fileSize) throws Exception {
        // 1. 列出并排序所有分片
        List<String> sourceObjectNames = listAndSortChunks(batchId);

        // 2. 构建最终对象路径并合并
        String finalObjectName = FilePathUtil.buildDateBasedPath(originalFileName, fileHash);
        try {
            objectStorageService.compose(getBucketName(), sourceObjectNames, finalObjectName);
        } catch (Exception e) {
            log.error("【文件合并 - {}】对象存储操作失败。最终对象: '{}'。", getStorageType(), finalObjectName, e);
            throw new Exception("对象存储操作失败", e);
        }

        // 3. 记录元信息, 如果失败则执行补偿
        FileMetadata metadata = buildFileMetadata(finalObjectName, originalFileName, fileSize, contentType, fileHash);
        try {
            fileMetadataRepository.save(metadata);
        } catch (Exception e) {
            log.error("【文件合并 - {}】保存元数据出错，将执行补偿操作。最终对象: '{}'。", getStorageType(), finalObjectName, e);
            // 补偿：删除已合并的文件
            try {
                objectStorageService.delete(getBucketName(), finalObjectName);
            } catch (Exception deleteEx) {
                log.error("【文件合并 - {}】补偿操作失败，删除对象 '{}' 时发生异常。", getStorageType(), finalObjectName, deleteEx);
            }
            throw new Exception("元数据保存失败，合并已回滚", e);
        }

        // 4. 异步删除临时分片
        log.info("【文件合并 - {}】文件合并成功，将异步清理临时分片。最终对象路径: '{}'。", getStorageType(), finalObjectName);
        triggerAsyncChunkCleanup(batchId, sourceObjectNames, getBucketName());

        return metadata;
    }


    // --- 私有辅助方法 ---

    /**
     * 获取分片存储桶名称。
     * @param batchId 分片上传批次ID
     * @return 分片存储桶名称
     * @throws Exception 如果获取分片存储桶名称时发生错误。
     */
    private List<String> listAndSortChunks(String batchId) throws Exception {
        List<StorageObject> chunks = objectStorageService.listObjects(getBucketName(), batchId + "/", true);
        if (chunks.isEmpty()) {
            throw new Exception("找不到任何分片进行合并，批次ID: " + batchId);
        }
        return chunks.stream()
                .map(StorageObject::getObjectName)
                .sorted(Comparator.comparing(s -> Integer.valueOf(s.substring(s.lastIndexOf('/') + 1))))
                .collect(Collectors.toList());
    }

    /**
     * 构建文件元数据
     * @param objectName 对象名
     * @param originalFileName 原始文件名
     * @param fileSize 文件大小
     * @param contentType 文件类型
     * @param fileHash 文件哈希值
     * @return 文件元数据
     */
    private FileMetadata buildFileMetadata(String objectName, String originalFileName, Long fileSize, String contentType, String fileHash) {
        FileMetadata metadata = new FileMetadata();
        metadata.setObjectName(objectName);
        metadata.setOriginalFilename(originalFileName);
        metadata.setFileSize(fileSize);
        metadata.setContentType(contentType);
        metadata.setContentHash(fileHash);
        metadata.setBucketName(getBucketName());
        metadata.setStorageType(getStorageType());
        return metadata;
    }
} 