package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.dto.CheckRequestDto;
import org.example.miniodemo.dto.FileDetailDto;
import org.example.miniodemo.dto.FileExistsDto;
import org.example.miniodemo.dto.FileUploadDto;
import org.example.miniodemo.dto.MergeRequestDto;
import org.example.miniodemo.service.PublicAssetService;
import org.example.miniodemo.service.impl.AbstractChunkedFileServiceImpl;
import org.example.miniodemo.service.impl.PublicAssetServiceImpl;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 处理公共资源（Public Assets）相关操作的API控制器。
 * <p>
 * 继承自 {@link BaseFileController}，复用了文件上传、删除等通用接口。
 * 所有此控制器下的端点都以 {@code /minio/public} 为前缀。
 */
@Slf4j
@RestController
@RequestMapping("/minio/public")
@RequiredArgsConstructor
public class PublicAssetController extends BaseFileController {

    private final PublicAssetService publicAssetService;

    @Override
    protected PublicAssetService getService() {
        return publicAssetService;
    }

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
            log.error("获取公共文件列表时出错", e);
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
                String url = publicAssetService.getPublicUrlFor(metadata.getFilePath());
                return R.success(new FileExistsDto(true, url));
            } else {
                return R.success(new FileExistsDto(false));
            }
        } catch (Exception e) {
            log.error("检查公共文件是否存在时出错: {}", e.getMessage(), e);
            return R.success(new FileExistsDto(false));
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
            FileMetadata metadata = publicAssetService.mergeChunks(mergeRequest);
            String url = publicAssetService.getPublicUrlFor(metadata.getFilePath());
            return R.success(url);
        } catch (Exception e) {
            log.error("合并公共文件分片时出错: {}", e.getMessage(), e);
            return R.error(ResultCode.MERGE_FAILED, "文件合并失败: " + e.getMessage());
        }
    }

    /**
     * 直接上传单个公共文件，并返回其公开访问URL。
     *
     * @param file          上传的文件
     * @param fileUploadDto 包含文件元数据（如哈希）的 DTO。
     * @return 包含文件公开URL的响应体
     */
    @Override
    @PostMapping("/upload/file")
    public R uploadFile(@RequestParam("file") MultipartFile file, @RequestPart("dto") FileUploadDto fileUploadDto) {
        if (file.isEmpty() || fileUploadDto.getFileHash().isBlank()) {
            log.warn("【直接上传-公共】请求缺少文件或文件哈希");
            return R.error(ResultCode.BAD_REQUEST, "文件和文件哈希不能为空");
        }

        try {
            log.info("【直接上传-公共】接收到文件上传请求: 文件名 [{}], 哈希 [{}]", file.getOriginalFilename(), fileUploadDto.getFileHash());
            FileMetadata metadata = getService().uploadFile(fileUploadDto.getFolderPath(),file, fileUploadDto.getFileHash());
            String url = publicAssetService.getPublicUrlFor(metadata.getFilePath());
            log.info("【直接上传-公共】文件上传成功，公开URL: {}", url);
            return R.success(url);
        } catch (Exception e) {
            log.error("【直接上传-公共】文件上传失败: {}", e.getMessage(), e);
            return R.error(ResultCode.FILE_UPLOAD_FAILED, "文件上传失败: " + e.getMessage());
        }
    }
}