package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.common.util.PathValidationUtil;
import org.example.miniodemo.dto.CheckRequestDto;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.dto.FileExistsDto;
import org.example.miniodemo.dto.MergeRequestDto;
import org.example.miniodemo.service.PrivateFileService;
import org.example.miniodemo.service.impl.AbstractChunkedFileServiceImpl;
import org.example.miniodemo.service.impl.PrivateFileServiceImpl;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 处理私有文件（Private Files）相关操作的API控制器。
 * <p>
 * "私有文件"是指存储在受限访问存储桶中的对象，必须通过预签名URL或后端代理才能访问。
 * 此控制器继承自 {@link BaseFileController}，复用了文件上传、删除等通用接口。
 * 所有此控制器下的端点都以 {@code /minio/private} 为前缀。
 */
@Slf4j
@RestController
@RequestMapping("/minio/private")
@RequiredArgsConstructor
public class PrivateFileController extends BaseFileController {

    private final PrivateFileService privateFileService;

    @Override
    protected PrivateFileService getService() {
        return privateFileService;
    }

    /**
     * 获取私有存储桶中所有已合并文件的列表。
     *
     * @return 包含所有私有文件详情的列表({@link FileDetailDto})。
     */
    @GetMapping("/list")
    public R<List<FileDetailDto>> listPrivateFiles() {
        try {
            List<FileDetailDto> fileList = privateFileService.listPrivateFiles();
            return R.success(fileList);
        } catch (Exception e) {
            log.error("获取私有文件列表失败: {}", e.getMessage(), e);
            return R.error(ResultCode.INTERNAL_SERVER_ERROR);
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
            log.error("检查文件是否存在失败: {}", e.getMessage(), e);
            return R.success(new FileExistsDto(false));
        }
    }

    /**
     * 通知服务器合并指定批次的所有分片。
     *
     * @param mergeRequest 包含文件合并所需全部信息的请求体 ({@link MergeRequestDto})。
     * @return 包含操作结果（成功或失败消息）的响应体。
     */
    @PostMapping("/upload/merge")
    public R<String> mergePrivateChunks(@RequestBody MergeRequestDto mergeRequest) {
        try {
            privateFileService.mergeChunks(mergeRequest);
            return R.success("文件合并成功: " + mergeRequest.getFileName());
        } catch (Exception e) {
            log.error("私有文件分片合并失败: {}", e.getMessage(), e);
            return R.error(ResultCode.MERGE_FAILED, "文件合并失败: " + e.getMessage());
        }
    }

    /**
     * 获取私有文件的预签名下载URL。
     *
     * @param filePath 需要下载的文件的完整对象路径。
     * @return 包含预签名URL的响应体。
     */
    @GetMapping("/download-url")
    public R<String> getPrivatePresignedDownloadUrl(@RequestParam("filePath") String filePath) {
        try {
            String safeFilePath = PathValidationUtil.clean(filePath);
            String url = privateFileService.getPresignedPrivateDownloadUrl(safeFilePath);
            return R.success(url);
        } catch (IllegalArgumentException e) {
            log.warn("检测到无效的私有文件路径: {}", filePath, e);
            return R.error(ResultCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("获取私有文件预签名URL失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_DOWNLOAD_FAILED, "获取 URL 失败: " + e.getMessage());
        }
    }

    /**
     * 通过后端服务器代理下载私有文件。
     * <p>
     * todo: 通常不使用，已经有预签名下载 URL
     *
     * @param filePath 需要下载的文件的完整对象路径。
     * @return 包含文件数据流的响应实体 ({@link Resource})。
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadPrivateFile(@RequestParam("filePath") String filePath) {
        try {
            String safeFileName = PathValidationUtil.clean(filePath);
            InputStream inputStream = privateFileService.downloadPrivateFile(safeFileName);
            String encodedFileName = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));

        } catch (IllegalArgumentException e) {
            log.warn("检测到无效的私有文件路径: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("私有文件下载失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}