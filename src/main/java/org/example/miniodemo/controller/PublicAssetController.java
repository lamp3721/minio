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
import org.example.miniodemo.service.PublicAssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
            FileMetadata metadata = publicAssetService.checkAndGetFileMetadata(checkRequest.getFileHash(), StorageType.PUBLIC);
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
     * 上传一个公共资源文件。
     *
     * @param file     通过 multipart/form-data 方式上传的文件。
     * @param fileHash 该文件的内容哈希值 (通常为MD5)，用于实现"秒传"和文件完整性校验。
     * @return 包含上传成功后文件的永久公开访问URL的响应体。
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