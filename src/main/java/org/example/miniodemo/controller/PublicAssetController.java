package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.dto.CheckRequestDto;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.dto.FileExistsDto;
import org.example.miniodemo.dto.MergeRequestDto;
import org.example.miniodemo.service.PublicAssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 处理公共资源（Public Assets）相关操作的API控制器。
 * <p>
 * "公共资源"是指存储在公开访问存储桶中的对象，通常是图片、CSS、JS等前端静态资源，
 * 它们可以通过直接的URL链接公开访问，无需签名。
 * 所有此控制器下的端点都以 {@code /minio/public} 为前缀。
 */
@Slf4j
@RestController
@RequestMapping("/minio/public")
@RequiredArgsConstructor
public class PublicAssetController {

    private final PublicAssetService publicAssetService;

    /**
     * 获取公共存储桶中所有文件的列表。
     *
     * @return 包含所有公共文件详情的列表({@link FileDetailDto})。
     */
    @GetMapping("/list")
    public R<List<FileDetailDto>> listPublicFiles() {
        try {
            return R.success(publicAssetService.listPublicFiles());
        } catch (Exception e) {
            log.error("获取公共文件列表失败", e);
            return R.error(ResultCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 检查目标文件是否存在于公共存储桶中（用于"秒传"功能）。
     * <p>
     * 在上传前，前端可以先调用此接口，通过文件哈希进行检查。
     *
     * @param checkRequest 包含文件哈希 (fileHash) 的请求体。
     * @return 包含检查结果的响应体 ({@link FileExistsDto})。如果文件已存在，会同时返回其公开访问URL。
     */
    @PostMapping("/check")
    public R<FileExistsDto> checkFileExists(@RequestBody CheckRequestDto checkRequest) {
        try {
            Optional<FileMetadata> metadataOptional = publicAssetService.checkFileExists(checkRequest.getFileHash());
            if (metadataOptional.isPresent()) {
                FileMetadata metadata = metadataOptional.get();
                String url = publicAssetService.getPublicUrlFor(metadata.getObjectName());
                return R.success(new FileExistsDto(true, url));
            } else {
                return R.success(new FileExistsDto(false));
            }
        } catch (Exception e) {
            log.error("检查公共文件失败: {}", e.getMessage(), e);
            // 出现异常时，为安全起见，返回false，让前端继续走上传流程
            return R.success(new FileExistsDto(false));
        }
    }

    /**
     * 上传单个公共文件分片。
     *
     * @param file        文件分片数据。
     * @param batchId     唯一批次ID。
     * @param chunkNumber 分片序号。
     * @return 操作结果。
     */
    @PostMapping("/upload/chunk")
    public R<String> uploadPublicChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("batchId") String batchId,
            @RequestParam("chunkNumber") Integer chunkNumber) {

        if (file.isEmpty() || batchId.isBlank()) {
            return R.error(ResultCode.BAD_REQUEST, "文件、批次ID或分片序号不能为空");
        }
        try {
            publicAssetService.uploadChunk(file, batchId, chunkNumber);
            return R.success("分片 " + chunkNumber + " 上传成功");
        } catch (Exception e) {
            log.error("公共库分片上传失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_UPLOAD_FAILED, "分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 检查并返回已上传的分片列表，用于断点续传。
     *
     * @param batchId 唯一标识本次文件上传任务的批次ID。
     * @return 包含已上传分片序号列表的响应体。
     */
    @GetMapping("/uploaded/chunks")
    public R<List<Integer>> getUploadedChunks(@RequestParam("batchId") String batchId) {
        try {
            List<Integer> chunkNumbers = publicAssetService.getUploadedChunkNumbers(batchId);
            return R.success(chunkNumbers);
        } catch (Exception e) {
            log.error("获取公共库已上传分片列表失败: {}", e.getMessage(), e);
            return R.error(ResultCode.INTERNAL_SERVER_ERROR, Collections.emptyList());
        }
    }

    /**
     * 通知服务器合并指定批次的所有公共文件分片。
     *
     * @param mergeRequest 包含文件合并所需全部信息的请求体。
     * @return 包含最终文件公开URL的响应体。
     */
    @PostMapping("/upload/merge")
    public R<String> mergePublicChunks(@RequestBody MergeRequestDto mergeRequest) {
        try {
            FileMetadata metadata = publicAssetService.mergeChunks(
                    mergeRequest.getBatchId(),
                    mergeRequest.getFileName(),
                    mergeRequest.getFileHash(),
                    mergeRequest.getContentType(),
                    mergeRequest.getFileSize()
            );
            String url = publicAssetService.getPublicUrlFor(metadata.getObjectName());
            return R.success(url);
        } catch (Exception e) {
            log.error("公共库文件合并失败: {}", e.getMessage(), e);
            return R.error(ResultCode.MERGE_FAILED, "文件合并失败: " + e.getMessage());
        }
    }

    /**
     * 删除一个公共文件。
     *
     * @param fileName 需要删除的文件的名称（即在MinIO中的完整对象路径）。
     * @return 包含操作结果（成功或失败消息）的响应体。
     */
    @DeleteMapping("/delete")
    public R<String> deletePublicFile(@RequestParam("fileName") String fileName) {
        try {
            publicAssetService.deletePublicFile(fileName);
            return R.success("文件删除成功: " + fileName);
        } catch (Exception e) {
            log.error("删除公共文件失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_DELETE_FAILED, "删除失败: " + e.getMessage());
        }
    }
} 