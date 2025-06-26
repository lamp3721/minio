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
import org.example.miniodemo.service.PrivateFileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 处理私有文件（Private Files）相关操作的API控制器。
 * <p>
 * "私有文件"是指存储在受限访问存储桶中的对象，必须通过预签名URL或后端代理才能访问，
 * 不能直接通过URL公开访问。
 * 此控制器包含了对私有文件进行增、删、查、改（通过重新上传）以及复杂的分片上传和下载的全部功能。
 * 所有此控制器下的端点都以 {@code /minio/private} 为前缀。
 */
@Slf4j
@RestController
@RequestMapping("/minio/private")
@RequiredArgsConstructor
public class PrivateFileController {

    private final PrivateFileService privateFileService;

    /**
     * 获取私有存储桶中所有已合并文件的列表。
     * <p>
     * 此接口会自动过滤掉分片上传过程中产生的临时文件。
     *
     * @return 包含所有私有文件详情的列表({@link FileDetailDto})。
     */
    @GetMapping("/list")
    public R<List<FileDetailDto>> listPrivateFiles() {
        try {
            List<FileDetailDto> fileList = privateFileService.listPrivateFiles();
            return R.success(fileList);
        } catch (Exception e) {
            log.error("获取文件列表失败: {}", e.getMessage(), e);
            return R.error(ResultCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 上传单个文件分片。
     * <p>
     * 这是分片上传流程的核心步骤，前端将文件切分后，为每个分片调用此接口。
     *
     * @param file        通过 multipart/form-data 方式上传的文件分片。
     * @param batchId     唯一标识本次文件上传任务的批次ID，由前端生成。
     * @param chunkNumber 当前分片的序号（从0开始）。
     * @return 包含操作结果（成功或失败消息）的响应体。
     */
    @PostMapping("/upload/chunk")
    public R<String> uploadPrivateChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("batchId") String batchId,
            @RequestParam("chunkNumber") Integer chunkNumber) {

        if (file.isEmpty() || batchId.isBlank()) {
            return R.error(ResultCode.BAD_REQUEST, "文件、批次ID或分片序号不能为空");
        }

        try {
            privateFileService.uploadChunk(file, batchId, chunkNumber);
            return R.success("分片 " + chunkNumber + " 上传成功");
        } catch (Exception e) {
            log.error("分片上传失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_UPLOAD_FAILED, "分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 检查目标文件是否存在于私有存储桶中（用于"秒传"功能）。
     *
     * @param checkRequest 包含文件哈希 (fileHash) 的请求体。
     * @return 包含检查结果的响应体({@link FileExistsDto})，其中只包含布尔值 `exists`。
     */
    @PostMapping("/check")
    public R<FileExistsDto> checkFileExists(@RequestBody CheckRequestDto checkRequest) {
        try {
            boolean exists = privateFileService.checkFileExists(checkRequest.getFileHash()).isPresent();
            return R.success(new FileExistsDto(exists));
        } catch (Exception e) {
            log.error("检查文件失败: {}", e.getMessage(), e);
            // 出现异常时，为安全起见，返回false，让前端继续走上传流程
            return R.success(new FileExistsDto(false));
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
            List<Integer> chunkNumbers = privateFileService.getUploadedChunkNumbers(batchId);
            return R.success(chunkNumbers);
        } catch (Exception e) {
            log.error("获取已上传分片列表失败: {}", e.getMessage(), e);
            return R.error(ResultCode.INTERNAL_SERVER_ERROR, Collections.emptyList());
        }
    }

    /**
     * 通知服务器合并指定批次的所有分片。
     * <p>
     * 这是分片上传流程的最后一步，在所有分片都成功上传后调用。
     *
     * @param mergeRequest 包含文件合并所需全部信息的请求体 ({@link MergeRequestDto})。
     * @return 包含操作结果（成功或失败消息）的响应体。
     */
    @PostMapping("/upload/merge")
    public R<String> mergePrivateChunks(@RequestBody MergeRequestDto mergeRequest) {
        try {
            privateFileService.mergeChunks(
                    mergeRequest.getBatchId(),
                    mergeRequest.getFileName(),
                    mergeRequest.getFileHash(),

                    mergeRequest.getContentType(),
                    mergeRequest.getFileSize()
            );

            return R.success("文件合并成功: " + mergeRequest.getFileName());
        } catch (Exception e) {
            log.error("文件合并失败: {}", e.getMessage(), e);
            return R.error(ResultCode.MERGE_FAILED, "文件合并失败: " + e.getMessage());
        }
    }

    /**
     * 获取私有文件的预签名下载URL。
     * <p>
     * 生成一个有时效性的URL，客户端可直接使用该URL从对象存储下载文件，数据不经过本应用服务器。
     * 这是推荐的高效下载方式。
     *
     * @param fileName 需要下载的文件的完整对象路径。
     * @return 包含预签名URL的响应体。
     */
    @GetMapping("/download-url")
    public R<String> getPrivatePresignedDownloadUrl(@RequestParam("fileName") String fileName) {
        try {
            String url = privateFileService.getPresignedPrivateDownloadUrl(fileName);
            return R.success(url);
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_DOWNLOAD_FAILED, "获取 URL 失败: " + e.getMessage());
        }
    }

    /**
     * 通过后端服务器代理下载私有文件。
     * <p>
     * 文件数据流会从对象存储流经本应用服务器，再转发给客户端。
     * 这种方式会占用服务器带宽和内存，但适用于需要在下载时进行额外权限控制或日志记录的场景。
     *
     * @param fileName 需要下载的文件的完整对象路径。
     * @return 包含文件数据流的响应实体 ({@link Resource})。
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadPrivateFile(@RequestParam("fileName") String fileName) {
        try {
            InputStream inputStream = privateFileService.downloadPrivateFile(fileName);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));

        } catch (Exception e) {
            log.error("文件下载失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 删除一个私有文件。
     *
     * @param fileName 需要删除的文件的完整对象路径。
     * @return 包含操作结果（成功或失败消息）的响应体。
     */
    @DeleteMapping("/delete")
    public R<String> deletePrivateFile(@RequestParam("fileName") String fileName) {
        try {
            privateFileService.deletePrivateFile(fileName);
            return R.success("文件删除成功: " + fileName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_DELETE_FAILED, "文件删除失败: " + e.getMessage());
        }
    }
} 