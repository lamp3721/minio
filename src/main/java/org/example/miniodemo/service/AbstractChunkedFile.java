package org.example.miniodemo.service;

import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.dto.MergeRequestDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface AbstractChunkedFile {

    // 检查文件是否存在
    Optional<FileMetadata> checkFileExists(String fileHash);

    // 上传一个分片
    String uploadChunk(MultipartFile file, String batchId, Integer chunkNumber) throws Exception;

    // 合并分片文件
    FileMetadata mergeChunks(MergeRequestDto mergeRequestDto) throws Exception;

    // 删除一个文件及其元数据。
    void deleteFile(String filePath) throws Exception;

    /**
     * 直接上传单个文件，适用于小文件。
     *
     * @param file     上传的文件
     * @param fileHash 文件的哈希值
     * @return 文件的元数据
     * @throws Exception 上传过程中发生错误
     */
    FileMetadata uploadFile(String folderPath,MultipartFile file, String fileHash) throws Exception;
}
