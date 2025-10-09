package org.example.miniodemo.service;

import org.example.miniodemo.common.response.R;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface AbstractChunkedFile {

    // === 新的会话管理方法 ===
    
    /**
     * 初始化上传会话
     */
    R<UploadSessionResponseDto> initUploadSession(InitUploadSessionDto initDto);
    
    /**
     * 获取上传会话状态
     */
    R<UploadSessionResponseDto> getUploadStatus(String sessionId);
    
    /**
     * 基于会话的分片上传
     */
    R<ChunkUploadResponseDto> uploadChunkWithSession(MultipartFile file, String sessionId, Integer chunkNumber);
    
    /**
     * 改进的合并分片方法
     */
    FileMetadata mergeChunksWithSession(ImprovedMergeRequestDto mergeRequestDto);

    // === 原有方法（保持兼容性） ===
    
    // 检查文件是否存在
    Optional<FileMetadata> checkFileExists(String fileHash);

    // 上传一个分片（旧方法，保持兼容性）
    String uploadChunk(MultipartFile file, String batchId, Integer chunkNumber);

    // 合并分片文件（旧方法，保持兼容性）
    FileMetadata mergeChunks(MergeRequestDto mergeRequestDto);

    // 删除一个文件及其元数据。
    void deleteFile(String filePath);

    /**
     * 直接上传单个文件，适用于小文件。
     *
     * @param file     上传的文件
     * @param fileHash 文件的哈希值
     * @return 文件的元数据
     */
    FileMetadata uploadFile(String folderPath, MultipartFile file, String fileHash);
}
