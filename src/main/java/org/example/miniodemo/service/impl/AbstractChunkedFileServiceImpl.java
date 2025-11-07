package org.example.miniodemo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.common.util.FilePathUtil;
import org.example.miniodemo.domain.ChunkUploadSession;
import org.example.miniodemo.domain.ChunkUploadStatus;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.*;
import org.example.miniodemo.event.EventPublisher;
import org.example.miniodemo.event.FileMergedEvent;
import org.example.miniodemo.exception.BusinessException;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.example.miniodemo.service.AbstractChunkedFile;
import org.example.miniodemo.service.AsyncFileService;
import org.example.miniodemo.service.ChunkUploadSessionService;
import org.example.miniodemo.service.storage.ObjectStorageService;
import org.example.miniodemo.domain.StorageObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
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
    
    @Autowired
    protected ChunkUploadSessionService sessionService;

    // --- 抽象方法，由子类实现 ---

    /**
     * 获取当前服务操作的存储桶名称。
     */
    protected abstract String getBucketName();

    /**
     * 获取当前服务的存储类型（PUBLIC 或 PRIVATE）。
     */
    protected abstract StorageType getStorageType();

    // --- 新的会话管理方法实现 ---

    @Override
    public R<UploadSessionResponseDto> initUploadSession(InitUploadSessionDto initDto) {
        try {
            // 先检查文件是否已存在（秒传）
            Optional<FileMetadata> existingFile = checkFileExists(initDto.getFileHash());
            if (existingFile.isPresent()) {
                log.info("【会话初始化 - {}】文件已存在，支持秒传: {}", getStorageType(), initDto.getFileHash());
                UploadSessionResponseDto response = new UploadSessionResponseDto();
                response.setSessionId(initDto.getFileHash());
                response.setStatus(ChunkUploadStatus.MERGED);
                response.setTotalChunks(initDto.getTotalChunks());
                response.setUploadedChunks(initDto.getTotalChunks());
                return R.success(response);
            }

            // 创建或获取上传会话
            ChunkUploadSession session = sessionService.createOrGetSession(
                initDto.getFileHash(), // 使用文件哈希作为会话ID
                initDto.getFileName(),
                initDto.getFileHash(),
                initDto.getFileSize(),
                initDto.getContentType(),
                initDto.getFolderPath(),
                initDto.getTotalChunks(),
                getBucketName(),
                getStorageType()
            );

            // 构建响应
            UploadSessionResponseDto response = new UploadSessionResponseDto();
            response.setSessionId(session.getSessionId());
            response.setStatus(session.getStatus());
            response.setTotalChunks(session.getTotalChunks());
            response.setUploadedChunks(session.getUploadedChunks());
            
            // 获取已上传的分片编号
            List<String> uploadedPaths = sessionService.getUploadedChunkPaths(session.getSessionId());
            List<Integer> uploadedChunkNumbers = new ArrayList<>();
            for (int i = 0; i < uploadedPaths.size(); i++) {
                if (uploadedPaths.get(i) != null && !uploadedPaths.get(i).isEmpty()) {
                    uploadedChunkNumbers.add(i + 1); // 分片编号从1开始
                }
            }
            response.setUploadedChunkNumbers(uploadedChunkNumbers);

            log.info("【会话初始化 - {}】会话创建成功: {}", getStorageType(), session.getSessionId());
            return R.success(response);
            
        } catch (Exception e) {
            log.error("【会话初始化 - {}】初始化上传会话失败", getStorageType(), e);
            return R.error(ResultCode.UPLOAD_SESSION_INIT_FAILED, "初始化上传会话失败: " + e.getMessage());
        }
    }

    @Override
    public R<UploadSessionResponseDto> getUploadStatus(String sessionId) {
        try {
            Optional<ChunkUploadSession> sessionOpt = sessionService.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return R.error(ResultCode.UPLOAD_SESSION_NOT_FOUND, "上传会话不存在");
            }

            ChunkUploadSession session = sessionOpt.get();
            UploadSessionResponseDto response = new UploadSessionResponseDto();
            response.setSessionId(session.getSessionId());
            response.setStatus(session.getStatus());
            response.setTotalChunks(session.getTotalChunks());
            response.setUploadedChunks(session.getUploadedChunks());

            // 获取已上传的分片编号
            List<String> uploadedPaths = sessionService.getUploadedChunkPaths(session.getSessionId());
            List<Integer> uploadedChunkNumbers = new ArrayList<>();
            for (int i = 0; i < uploadedPaths.size(); i++) {
                if (uploadedPaths.get(i) != null && !uploadedPaths.get(i).isEmpty()) {
                    uploadedChunkNumbers.add(i + 1);
                }
            }
            response.setUploadedChunkNumbers(uploadedChunkNumbers);

            return R.success(response);
            
        } catch (Exception e) {
            log.error("【会话状态 - {}】获取上传状态失败: {}", getStorageType(), sessionId, e);
            return R.error(ResultCode.UPLOAD_SESSION_QUERY_FAILED, "获取上传状态失败: " + e.getMessage());
        }
    }

    @Override
    public R<ChunkUploadResponseDto> uploadChunkWithSession(MultipartFile file, String sessionId, Integer chunkNumber) {
        try {
            // 第一层验证：会话ID必须存在（双重验证，确保安全）
            if (sessionId == null || sessionId.isBlank()) {
                log.error("【安全拦截 - {}】企图在没有会话ID的情况下上传分片", getStorageType());
                return R.error(ResultCode.BAD_REQUEST, "会话ID不能为空，必须先调用/upload/init初始化会话");
            }

            // 第二层验证：会话必须在数据库中存在
            Optional<ChunkUploadSession> sessionOpt = sessionService.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("【安全拦截 - {}】使用不存在的会话ID尝试上传: {}", getStorageType(), sessionId);
                return R.error(ResultCode.UPLOAD_SESSION_NOT_FOUND, 
                    "上传会话不存在或已过期，请重新调用/upload/init初始化会话");
            }

            // 第三层验证：会话状态必须允许上传
            ChunkUploadSession session = sessionOpt.get();
            if (session.getStatus() == ChunkUploadStatus.MERGED) {
                log.warn("【安全拦截 - {}】会话已完成合并，不允许继续上传: {}", getStorageType(), sessionId);
                return R.error(ResultCode.UPLOAD_SESSION_STATE_MISMATCH, "会话已完成，无需再上传分片");
            }
            if (session.getStatus() == ChunkUploadStatus.EXPIRED) {
                log.warn("【安全拦截 - {}】会话已过期: {}", getStorageType(), sessionId);
                return R.error(ResultCode.UPLOAD_SESSION_STATE_MISMATCH, "会话已过期，请重新初始化");
            }

            // 第四层验证：会话存储类型必须匹配
            if (!session.getStorageType().equals(getStorageType())) {
                log.error("【安全拦截 - {}】会话存储类型不匹配: 期望={}, 实际={}", 
                    getStorageType(), getStorageType(), session.getStorageType());
                return R.error(ResultCode.BAD_REQUEST, "会话存储类型不匹配");
            }

            // 第五层验证：分片编号必须在合法范围内
            if (chunkNumber < 1 || chunkNumber > session.getTotalChunks()) {
                log.warn("【安全拦截 - {}】分片编号超出范围: {}/{}", 
                    getStorageType(), chunkNumber, session.getTotalChunks());
                return R.error(ResultCode.BAD_REQUEST, 
                    String.format("分片编号无效，必须在1-%d之间", session.getTotalChunks()));
            }

            // 上传分片
            String chunkPath = sessionId + "/" + chunkNumber;
            try (InputStream inputStream = file.getInputStream()) {
                objectStorageService.upload(
                    getBucketName(),
                    chunkPath,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
                );
            }

            // 记录分片上传成功
            sessionService.recordChunkUploaded(sessionId, chunkNumber, chunkPath);

            log.info("【分片上传 - {}】分片上传成功: 会话={}, 分片={}", getStorageType(), sessionId, chunkNumber);
            return R.success(new ChunkUploadResponseDto(chunkNumber, chunkPath));
            
        } catch (Exception e) {
            log.error("【分片上传 - {}】分片上传失败: 会话={}, 分片={}", getStorageType(), sessionId, chunkNumber, e);
            return R.error(ResultCode.FILE_UPLOAD_FAILED, "分片上传失败: " + e.getMessage());
        }
    }

    @Override
    public FileMetadata mergeChunksWithSession(ImprovedMergeRequestDto mergeRequestDto) {
        String sessionId = mergeRequestDto.getSessionId();
        
        try {
            // 验证会话
            Optional<ChunkUploadSession> sessionOpt = sessionService.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                throw new BusinessException(ResultCode.UPLOAD_SESSION_NOT_FOUND, "上传会话不存在");
            }

            ChunkUploadSession session = sessionOpt.get();
            
            log.info("【文件合并 - {}】开始验证会话: 会话={}, 文件={}", getStorageType(), sessionId, mergeRequestDto.getFileName());
            
            // 验证文件哈希
            if (!session.getFileHash().equals(mergeRequestDto.getFileHash())) {
                log.error("【文件合并 - {}】文件哈希不匹配: 会话={}, 期望={}, 实际={}", 
                         getStorageType(), sessionId, session.getFileHash(), mergeRequestDto.getFileHash());
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "文件哈希不匹配");
            }

            // 验证会话状态
            boolean readyToMerge = sessionService.isReadyToMerge(sessionId);
            // 重新获取最新会话状态用于日志对比
            Optional<ChunkUploadSession> latestSessionOpt = sessionService.getSession(sessionId);
            ChunkUploadSession latestSession = latestSessionOpt.orElse(session);
            log.info("【文件合并 - {}】合并校验: 会话={}, 可合并={}, 原状态={}, 当前状态={}, 已上传={}/{}",
                    getStorageType(), sessionId, readyToMerge, session.getStatus(), latestSession.getStatus(),
                    latestSession.getUploadedChunks(), latestSession.getTotalChunks());
            if (!readyToMerge) {
                throw new BusinessException(ResultCode.UPLOAD_SESSION_STATE_MISMATCH,
                        String.format("会话状态不允许合并，请确保所有分片已上传。当前状态: %s, 已上传: %d/%d",
                                latestSession.getStatus(), latestSession.getUploadedChunks(), latestSession.getTotalChunks()));
            }

            // 更新会话状态为合并中
            sessionService.updateSessionStatus(sessionId, ChunkUploadStatus.MERGING);

            // 获取所有分片路径
            List<String> chunkPaths = sessionService.getUploadedChunkPaths(sessionId);
            log.info("【文件合并 - {}】获取分片路径: 会话={}, 分片数={}, 期望数={}", 
                    getStorageType(), sessionId, chunkPaths.size(), session.getTotalChunks());
            
            if (chunkPaths.isEmpty()) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "未找到任何分片，无法合并");
            }
            
            if (chunkPaths.size() != session.getTotalChunks()) {
                log.error("【文件合并 - {}】分片数量不匹配: 会话={}, 实际={}, 期望={}", 
                         getStorageType(), sessionId, chunkPaths.size(), session.getTotalChunks());
                throw new BusinessException(ResultCode.VALIDATE_FAILED, 
                    String.format("分片数量不匹配，实际: %d, 期望: %d", chunkPaths.size(), session.getTotalChunks()));
            }

            // 校验每个分片路径非空且编号完整
            List<Integer> missingIndices = new ArrayList<>();
            for (int i = 0; i < session.getTotalChunks(); i++) {
                String path = chunkPaths.get(i);
                if (path == null || path.isEmpty()) {
                    missingIndices.add(i + 1);
                } else {
                    String expectedPrefix = sessionId + "/";
                    if (!path.startsWith(expectedPrefix)) {
                        log.warn("【文件合并 - {}】分片路径前缀异常: 会话={}, 索引={}, 路径={}", getStorageType(), sessionId, i + 1, path);
                    }
                    String expectedExact = expectedPrefix + (i + 1);
                    if (!path.equals(expectedExact)) {
                        log.warn("【文件合并 - {}】分片路径与预期编号不一致: 期望={}, 实际={}", expectedExact, path);
                    }
                }
            }
            if (!missingIndices.isEmpty()) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED,
                        String.format("分片缺失，编号: %s", missingIndices));
            }

            // 通过对象存储校验分片确实存在
            try {
                List<StorageObject> storedChunks = objectStorageService.listObjects(getBucketName(), sessionId + "/", true);
                java.util.Set<String> existingPaths = storedChunks.stream()
                        .map(StorageObject::getFilePath)
                        .collect(java.util.stream.Collectors.toSet());
                List<Integer> notFound = new ArrayList<>();
                for (int i = 1; i <= session.getTotalChunks(); i++) {
                    String expectedPath = sessionId + "/" + i;
                    if (!existingPaths.contains(expectedPath)) {
                        notFound.add(i);
                    }
                }
                if (!notFound.isEmpty()) {
                    log.error("【文件合并 - {}】对象存储缺少分片: 会话={}, 缺失编号={}", getStorageType(), sessionId, notFound);
                    throw new BusinessException(ResultCode.VALIDATE_FAILED,
                            String.format("对象存储缺少分片，编号: %s", notFound));
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("【文件合并 - {}】校验分片存在性失败: 会话={}", getStorageType(), sessionId, e);
                throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "校验分片存在性失败: " + e.getMessage(), e);
            }

            // 构建最终文件路径
            String finalFilePath = FilePathUtil.buildDateBasedPath(
                mergeRequestDto.getFolderPath(), 
                mergeRequestDto.getFileHash(), 
                mergeRequestDto.getFileName()
            );

            // 执行合并
            objectStorageService.compose(getBucketName(), chunkPaths, finalFilePath);
            log.info("【文件合并 - {}】对象存储操作成功。最终对象: '{}'。", getStorageType(), finalFilePath);

            // 构建文件元数据
            FileMetadata metadata = buildFileMetadataFromSession(session, finalFilePath);

            // 发布文件合并成功事件
            FileMergedEvent event = new FileMergedEvent(this, metadata, sessionId, chunkPaths);
            eventPublisher.publish(event);

            // 更新会话状态为已合并
            sessionService.updateSessionStatus(sessionId, ChunkUploadStatus.MERGED);

            // 不再即时删除，改由定时清理任务统一清理 MERGED 会话

            log.info("【文件合并 - {}】文件合并成功: 会话={}, 最终路径={}", getStorageType(), sessionId, finalFilePath);
            return metadata;
            
        } catch (BusinessException e) {
            // 业务异常直接重新抛出，不更新会话状态（因为可能是验证失败等）
            log.error("【文件合并 - {}】业务异常: 会话={}, 错误={}", getStorageType(), sessionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            // 其他异常（如IO异常、MinIO异常等）更新会话状态为失败
            sessionService.updateSessionStatus(sessionId, ChunkUploadStatus.FAILED);
            log.error("【文件合并 - {}】文件合并失败: 会话={}", getStorageType(), sessionId, e);
            throw new BusinessException(ResultCode.UPLOAD_SESSION_STATE_MISMATCH, "文件合并失败: " + e.getMessage(), e);
        }
    }


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
     */
    public String uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) {
        String filePath = batchId + "/" + chunkNumber;
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    getBucketName(),
                    filePath,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        } catch (Exception e) {
            log.error("【分片上传 - {}】分片上传失败，文件路径: '{}'", getStorageType(), filePath, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "分片上传失败", e);
        }
        return filePath;
    }

    /**
     * 合并分片文件。
     * <p>
     * 此方法现在只负责对象存储层面的合并操作，并发布一个 {@link FileMergedEvent} 事件。
     * 后续的数据库持久化和分片清理将由事件监听器异步处理。
     *
     * @return 合并后的文件元数据（此时尚未持久化）。
     */
    public FileMetadata mergeChunks(MergeRequestDto mergeRequestDto) {
        // 1. 从DTO获取分片路径并按编号排序
        List<String> sourceObjectNames = mergeRequestDto.getChunkPaths().stream()
                .sorted(Comparator.comparing(s -> Integer.parseInt(s.substring(s.lastIndexOf('/') + 1))))
                .collect(Collectors.toList());

        // 如果分片列表为空，则抛出异常
        if (sourceObjectNames.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "分片列表为空，无法合并。批次ID: " + mergeRequestDto.getBatchId());
        }

        // 2. 构建最终对象路径并合并
        String finalFilePath = FilePathUtil.buildDateBasedPath(mergeRequestDto.getFolderPath(), mergeRequestDto.getFileHash(), mergeRequestDto.getFileName());
        try {
            objectStorageService.compose(getBucketName(), sourceObjectNames, finalFilePath);
            log.info("【文件合并 - {}】对象存储操作成功。最终对象: '{}'。", getStorageType(), finalFilePath);
        } catch (Exception e) {
            log.error("【文件合并 - {}】对象存储操作失败。最终对象: '{}'。", getStorageType(), finalFilePath, e);
            // 优雅地处理 MinIO 特定异常
            if (e instanceof io.minio.errors.ErrorResponseException) {
                io.minio.errors.ErrorResponseException ere = (io.minio.errors.ErrorResponseException) e;
                String code = ere.errorResponse().code();
                if ("InvalidPart".equals(code) || "InvalidPartOrder".equals(code)) {
                    throw new BusinessException(ResultCode.MERGE_INVALID_PART, "分片无效或顺序错误", e);
                } else if ("NoSuchKey".equals(code)) {
                    throw new BusinessException(ResultCode.MERGE_SOURCE_NOT_FOUND, "源分片丢失", e);
                }
            }
            // 对于其他所有异常，或非特定 MinIO 异常，可以抛出一个更通用的业务异常
            throw new BusinessException(ResultCode.UPLOAD_SESSION_STATE_MISMATCH, "文件合并失败，请检查上传状态或重试", e);
        }

        // 3. 构建元数据对象
        FileMetadata metadata = this.buildFileMetadata(mergeRequestDto, finalFilePath);

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
     */
    @Transactional
    public void deleteFile(String filePath) {
        try {
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
        } catch (Exception e) {
            log.error("【文件删除 - {}】删除对象存储文件失败: '{}'", getStorageType(), filePath, e);
            throw new BusinessException(ResultCode.FILE_DELETE_FAILED, "文件删除失败", e);
        }
    }


    // --- 私有辅助方法 ---

    /**
     * 直接上传单个文件，适用于小文件。
     *
     * @param file     上传的文件
     * @param fileHash 文件的哈希值
     * @return 文件的元数据
     */
    @Override
    @Transactional
    public FileMetadata uploadFile(String folderPath, MultipartFile file, String fileHash) {
        log.info("【直接上传 - {}】开始处理直接上传请求，文件名: {}，哈希: {}", getStorageType(), file.getOriginalFilename(), fileHash);

        // 1. 构建最终对象路径
        String finalFilePath = FilePathUtil.buildDateBasedPath(folderPath, fileHash, file.getOriginalFilename());
        log.debug("【直接上传 - {}】构建最终文件路径: {}", getStorageType(), finalFilePath);

        // 2. 上传文件到对象存储
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    getBucketName(),
                    finalFilePath,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
            log.info("【直接上传 - {}】文件已成功上传到对象存储。最终对象: '{}'。", getStorageType(), finalFilePath);
        } catch (Exception e) {
            log.error("【直接上传 - {}】文件上传到对象存储时失败。最终对象: '{}'。", getStorageType(), finalFilePath, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "文件上传失败", e);
        }

        // 3. 构建并保存文件元数据
        FileMetadata metadata = new FileMetadata();
        metadata.setFolderPath(folderPath);
        metadata.setFilePath(finalFilePath);
        metadata.setOriginalFilename(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setContentHash(fileHash);
        metadata.setBucketName(getBucketName());
        metadata.setStorageType(getStorageType());

        fileMetadataRepository.save(metadata);
        log.info("【直接上传 - {}】文件元数据已成功保存到数据库。最终对象路径: '{}'。", getStorageType(), finalFilePath);

        return metadata;
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

    /**
     * 从会话构建文件元数据
     *
     * @param session  上传会话
     * @param filePath 文件在存储中的完整路径
     * @return 文件元数据
     */
    private FileMetadata buildFileMetadataFromSession(ChunkUploadSession session, String filePath) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFolderPath(session.getFolderPath());
        metadata.setFilePath(filePath);
        metadata.setOriginalFilename(session.getFileName());
        metadata.setFileSize(session.getFileSize());
        metadata.setContentType(session.getContentType());
        metadata.setContentHash(session.getFileHash());
        metadata.setBucketName(session.getBucketName());
        metadata.setStorageType(session.getStorageType());
        return metadata;
    }
}
