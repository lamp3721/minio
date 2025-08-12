package org.example.miniodemo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageObject;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.MergeRequestDto;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.AbstractChunkedFile;
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.example.miniodemo.event.EventPublisher;
import org.example.miniodemo.event.FileMergedEvent;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 抽象分片文件服务，封装了分片上传、合并和秒传的通用逻辑。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractChunkedFileServiceImpl implements AbstractChunkedFile {

    protected final ObjectStorageService objectStorageService;
    protected final FileMetadataRepository fileMetadataRepository;
    protected final AsyncFileService asyncFileService;
    protected final EventPublisher eventPublisher;

    // --- 抽象方法，由子类实现 ---

    /**
     * 获取当前服务操作的存储桶名称。
     */
    protected abstract String getBucketName();

    /**
     * 获取当前服务的存储类型（PUBLIC 或 PRIVATE）。
     */
    protected abstract StorageType getStorageType();


    /**
     * 检查文件是否存在
     *
     * @param fileHash 文件哈希值
     * @return 文件元数据
     */
    public Optional<FileMetadata> checkFileExists(String fileHash) {
        return fileMetadataRepository.findByHash(fileHash, getStorageType());
    }

    /**
     * 上传一个分片
     *
     * @param file        文件
     * @param batchId     批次ID
     * @param chunkNumber 分片序号
     * @throws Exception 如果上传分片时发生错误。
     */
    public void uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) throws Exception {
        String filePath = batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    getBucketName(),
                    filePath,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        }
    }

    /**
     * 获取已上传的分片列表。
     *
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
                        return Integer.parseInt(chunk.getFilePath().substring(chunk.getFilePath().lastIndexOf('/') + 1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 合并分片文件。
     * <p>
     * 此方法现在只负责对象存储层面的合并操作，并发布一个 {@link FileMergedEvent} 事件。
     * 后续的数据库持久化和分片清理将由事件监听器异步处理。
     *
     * @return 合并后的文件元数据（此时尚未持久化）。
     * @throws Exception 如果列出或合并分片时发生错误。
     */
    public FileMetadata mergeChunks(MergeRequestDto mergeRequestDto) throws Exception {
        // 1. 列出并排序所有分片  cf17ce6f77e88fefd44ccb2f0e751967/0  加上桶即使完整路径
        List<String> sourceObjectNames = listAndSortChunks(mergeRequestDto.getBatchId());

        // 2. 构建最终对象路径并合并
        String finalFilePath = FilePathUtil.buildDateBasedPath(mergeRequestDto.getFolderPath(), mergeRequestDto.getFileHash(), mergeRequestDto.getFileName());
        try {
            objectStorageService.compose(getBucketName(), sourceObjectNames, finalFilePath);
            log.info("【文件合并 - {}】对象存储操作成功。最终对象: '{}'。", getStorageType(), finalFilePath);
        } catch (Exception e) {
            log.error("【文件合并 - {}】对象存储操作失败。最终对象: '{}'。", getStorageType(), finalFilePath, e);
            throw new Exception("对象存储操作失败", e);
        }

        // 3. 构建元数据对象
        FileMetadata metadata = buildFileMetadata(mergeRequestDto, finalFilePath);

        // 4. 发布文件合并成功事件
        FileMergedEvent event = new FileMergedEvent(this, metadata, mergeRequestDto.getBatchId(), sourceObjectNames);
        eventPublisher.publish(event);
        log.info("【文件合并 - {}】文件合并成功事件已发布。最终对象路径: '{}'。", getStorageType(), finalFilePath);

        return metadata;
    }

    /**
     * 删除一个文件及其元数据。
     *
     * @param filePath 需要删除的文件的对象路径。
     * @throws Exception 如果删除过程中发生错误。
     */
    @Transactional
    public void deleteFile(String filePath) throws Exception {
        // 1. 从对象存储中删除文件
        objectStorageService.delete(getBucketName(), filePath);

        // 2. 从数据库中删除元数据
        String hash = FilePathUtil.extractHashFromPath(filePath);
        if (hash != null) {
            fileMetadataRepository.deleteByHash(hash, getStorageType());
            log.info("【文件删除 - {}】成功删除文件元数据，Hash: {}", getStorageType(), hash);
        } else {
            log.warn("【文件删除 - {}】无法从路径中提取Hash，可能未删除元数据: {}", getStorageType(), filePath);
        }
        log.info("【文件删除 - {}】成功删除对象: {}", getStorageType(), filePath);
    }


    // --- 私有辅助方法 ---


    /**
     * 列出指定批次ID下的所有分片对象，并按分片编号进行排序。
     * <p>
     * 该方法会调用对象存储服务，获取批次目录下的所有文件（分片），
     * 并根据文件名中最后的数字（假设为分片编号）进行升序排序，方便后续合并操作。
     *
     * @param batchId 批次ID，对应存储桶中的目录前缀
     * @return 按分片编号排序后的分片文件路径列表
     * @throws Exception 如果找不到任何分片文件，则抛出异常
     */
    private List<String> listAndSortChunks(String batchId) throws Exception {
        // 调用对象存储服务列出该批次目录下所有分片对象，递归查询
        List<StorageObject> chunks = objectStorageService.listObjects(getBucketName(), batchId + "/", true);

        // 如果没有找到任何分片，抛出异常提示
        if (chunks.isEmpty()) {
            throw new Exception("找不到任何分片进行合并，批次ID: " + batchId);
        }

        // 将分片对象转为文件路径列表，并根据路径中最后一个 '/' 后的数字部分排序（分片编号）   cf17ce6f77e88fefd44ccb2f0e751967/0
        return chunks.stream()
                .map(StorageObject::getFilePath)
                .sorted(Comparator.comparing(s -> Integer.valueOf(s.substring(s.lastIndexOf('/') + 1))))
                .collect(Collectors.toList());
    }

    /**
     * 构建文件元数据
     *
     * @param mergeRequestDto 包含文件元数据信息的请求DTO
     * @param filePath        文件在存储中的完整路径
     * @return 文件元数据
     */
    private FileMetadata buildFileMetadata(MergeRequestDto mergeRequestDto, String filePath) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFolderPath(mergeRequestDto.getFolderPath());
        metadata.setFilePath(filePath);
        metadata.setOriginalFilename(mergeRequestDto.getFileName());
        metadata.setFileSize(mergeRequestDto.getFileSize());
        metadata.setContentType(mergeRequestDto.getContentType());
        metadata.setContentHash(mergeRequestDto.getFileHash());
        metadata.setBucketName(getBucketName());
        metadata.setStorageType(getStorageType());
        return metadata;
    }
} 