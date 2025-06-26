package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.dto.CheckRequestDto;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.dto.FileExistsDto;
import org.example.miniodemo.service.PublicAssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 专用于处理公共资源（Public Assets）相关操作的API控制器。
 * <p>
 * "公共资源"是指存储在公开访问存储桶中的对象，通常是图片、CSS、JS文件等，
 * 可以通过直接的URL链接被浏览器或客户端访问，无需签名。
 * 所有此控制器下的端点都以 {@code /minio/public} 为前缀。
 */
@Slf4j
@RestController
@RequestMapping("/minio/public")
@RequiredArgsConstructor
public class PublicAssetController {

    private final PublicAssetService publicAssetService;

    /**
     * GET /list : 获取公共存储桶中所有文件的列表。
     *
     * @return {@link ResponseEntity} 包含一个DTO列表的响应实体，每个DTO代表一个文件。
     *         成功时返回文件列表和HTTP状态码200 (OK)。
     *         如果发生内部错误，则返回一个空列表和HTTP状态码500 (Internal Server Error)。
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
     * POST /check : 检查文件是否已存在（用于秒传）。
     *
     * @param checkRequest 包含文件哈希 (fileHash) 和原始文件名 (fileName) 的请求体。
     * @return {@link ResponseEntity} 返回一个包含布尔值的DTO "exists"。
     */
    @PostMapping("/check")
    public R<FileExistsDto> checkFileExists(@RequestBody CheckRequestDto checkRequest) {
        try {
            FileMetadata metadata = publicAssetService.checkAndGetFileMetadata(checkRequest.getFileHash());
            if (metadata != null) {
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
     * POST /upload : 上传一个公开的图片文件。
     *
     * @param file 由multipart/form-data请求体中名为 "file" 的部分承载的上传文件。不能为空。
     * @param fileHash 文件的MD5哈希值。
     * @return {@link ResponseEntity} 包含上传成功后文件的永久公开访问URL的响应实体。
     *         成功时返回URL字符串和HTTP状态码200 (OK)。
     *         失败时返回错误信息和HTTP状态码500 (Internal Server Error)。
     */
    @PostMapping("/upload")
    public R<String> uploadPublicImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileHash") String fileHash
    ) {
        try {
            String url = publicAssetService.uploadPublicImage(file, fileHash);
            return R.success(url);
        } catch (Exception e) {
            log.error("上传公共图片失败", e);
            return R.error(ResultCode.FILE_UPLOAD_FAILED, "上传失败");
        }
    }

    /**
     * DELETE /delete : 删除一个公共文件。
     *
     * @param fileName 需要删除的文件的名称（即MinIO中的对象名称）。通过请求参数传递。
     * @return {@link ResponseEntity} 包含操作结果字符串的响应实体。
     *         成功时返回 "文件删除成功: [文件名]" 和HTTP状态码200 (OK)。
     *         失败时返回错误信息和HTTP状态码500 (Internal Server Error)。
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