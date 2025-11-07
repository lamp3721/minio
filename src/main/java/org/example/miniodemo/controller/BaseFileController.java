package org.example.miniodemo.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.common.util.PathValidationUtil;
import org.example.miniodemo.dto.*;
import org.example.miniodemo.service.AbstractChunkedFile;
import org.example.miniodemo.service.ChunkUploadSessionService;
import org.example.miniodemo.service.impl.PrivateFileServiceImpl;
import org.example.miniodemo.service.impl.PublicAssetServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 抽象文件控制器，封装了文件上传和管理的通用API端点。
 * <p>
 * 通过定义一个抽象的 {@link #getService()} 方法，使得子类能够注入具体的文件服务
 * (例如 {@link PublicAssetServiceImpl}
 * 或 {@link PrivateFileServiceImpl})，
 * 从而复用这些通用的API逻辑。
 */
@Slf4j
public abstract class BaseFileController {

    @Autowired
    protected ChunkUploadSessionService sessionService;

    /**
     * 抽象方法，由子类实现，用于提供具体的文件服务实例。
     *
     * @return 继承自 {@link AbstractChunkedFile} 的服务实例。
     */
    protected abstract AbstractChunkedFile getService();

    /**
     * 初始化上传会话
     */
    @PostMapping("/upload/init")
    public R<UploadSessionResponseDto> initUploadSession(@RequestBody InitUploadSessionDto initDto) {
        if (initDto.getFileName() == null || initDto.getFileHash() == null || 
            initDto.getFileSize() == null || initDto.getTotalChunks() == null) {
            return R.error(ResultCode.BAD_REQUEST, "文件名、哈希值、大小和分片数不能为空");
        }

        return getService().initUploadSession(initDto);
    }

    /**
     * 获取上传会话状态
     */
    @GetMapping("/upload/status/{sessionId}")
    public R<UploadSessionResponseDto> getUploadStatus(@PathVariable String sessionId) {
        return getService().getUploadStatus(sessionId);
    }

    /**
     * 改进的上传文件分片端点（强制要求会话ID）
     */
    @PostMapping("/upload/chunk")
    public R<ChunkUploadResponseDto> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("chunkNumber") Integer chunkNumber) {

        // 严格验证：文件不能为空
        if (file == null || file.isEmpty()) {
            log.warn("【分片上传拦截】文件为空");
            return R.error(ResultCode.BAD_REQUEST, "文件不能为空");
        }

        // 严格验证：会话ID必须存在且不能为空白字符串
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("【分片上传拦截】会话ID为空或无效");
            return R.error(ResultCode.BAD_REQUEST, "会话ID不能为空，所有上传都必须先通过init接口初始化会话");
        }

        // 验证：会话ID长度和格式（防止恶意输入）
        if (sessionId.length() > 100 || sessionId.length() < 10) {
            log.warn("【分片上传拦截】会话ID长度异常: {}", sessionId.length());
            return R.error(ResultCode.BAD_REQUEST, "会话ID格式无效");
        }

        // 验证：分片序号必须存在
        if (chunkNumber == null || chunkNumber < 1) {
            log.warn("【分片上传拦截】分片序号无效: {}", chunkNumber);
            return R.error(ResultCode.BAD_REQUEST, "分片序号必须大于0");
        }

        log.info("【分片上传请求】会话ID={}, 分片序号={}, 文件大小={}", 
                sessionId.substring(0, Math.min(8, sessionId.length())), chunkNumber, file.getSize());

        return getService().uploadChunkWithSession(file, sessionId, chunkNumber);
    }

    /**
     * 通用的文件删除端点。
     *
     * @param filePath 需要删除的文件的完整对象路径。
     * @return 包含操作结果的响应体。
     */
    @DeleteMapping("/delete")
    public R<String> deleteFile(@RequestParam(required = false) String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return R.error(ResultCode.BAD_REQUEST, "文件名不能为空");
        }
        String safeFileName = PathValidationUtil.clean(filePath);
        getService().deleteFile(safeFileName);
        return R.success("文件删除成功: " + safeFileName);
    }

    public abstract R<?> uploadFile(MultipartFile file, FileUploadDto fileUploadDto);
}