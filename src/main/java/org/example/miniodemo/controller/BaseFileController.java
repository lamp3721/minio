package org.example.miniodemo.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.common.util.PathValidationUtil;
import org.example.miniodemo.service.AbstractChunkedFileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * 抽象文件控制器，封装了文件上传和管理的通用API端点。
 * <p>
 * 通过定义一个抽象的 {@link #getService()} 方法，使得子类能够注入具体的文件服务
 * (例如 {@link org.example.miniodemo.service.PublicAssetService}
 * 或 {@link org.example.miniodemo.service.PrivateFileService})，
 * 从而复用这些通用的API逻辑。
 */
@Slf4j
public abstract class BaseFileController {

    /**
     * 抽象方法，由子类实现，用于提供具体的文件服务实例。
     *
     * @return 继承自 {@link AbstractChunkedFileService} 的服务实例。
     */
    protected abstract AbstractChunkedFileService getService();

    /**
     * 通用的上传文件分片端点。
     *
     * @param file        通过 multipart/form-data 方式上传的文件分片。
     * @param batchId     唯一标识本次文件上传任务的批次ID。
     * @param chunkNumber 当前分片的序号。
     * @return 包含操作结果的响应体。
     */
    @PostMapping("/upload/chunk")
    public R<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("batchId") String batchId,
            @RequestParam("chunkNumber") Integer chunkNumber) {

        if (file.isEmpty() || batchId.isBlank()) {
            return R.error(ResultCode.BAD_REQUEST, "文件、批次ID或分片序号不能为空");
        }

        try {
            getService().uploadChunk(file, batchId, chunkNumber);
            return R.success("分片 " + chunkNumber + " 上传成功");
        } catch (Exception e) {
            log.error("分片上传失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_UPLOAD_FAILED, "分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 通用的获取已上传分片列表端点，用于断点续传。
     *
     * @param batchId 唯一标识本次文件上传任务的批次ID。
     * @return 包含已上传分片序号列表的响应体。
     */
    @GetMapping("/uploaded/chunks")
    public R<List<Integer>> getUploadedChunks(@RequestParam("batchId") String batchId) {
        try {
            List<Integer> chunkNumbers = getService().getUploadedChunkNumbers(batchId);
            return R.success(chunkNumbers);
        } catch (Exception e) {
            log.error("获取已上传分片列表失败: {}", e.getMessage(), e);
            return R.error(ResultCode.INTERNAL_SERVER_ERROR, Collections.emptyList());
        }
    }

    /**
     * 通用的文件删除端点。
     *
     * @param fileName 需要删除的文件的完整对象路径。
     * @return 包含操作结果的响应体。
     */
    @DeleteMapping("/delete")
    public R<String> deleteFile(@RequestParam("fileName") String fileName) {
        try {
            String safeFileName = PathValidationUtil.clean(fileName);
            getService().deleteFile(safeFileName);
            return R.success("文件删除成功: " + safeFileName);
        } catch (IllegalArgumentException e) {
            log.warn("检测到无效的文件路径: {}", fileName, e);
            return R.error(ResultCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("删除文件失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_DELETE_FAILED, "删除失败: " + e.getMessage());
        }
    }
} 