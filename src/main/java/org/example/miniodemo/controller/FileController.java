package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.config.MinioBucketConfig;
import org.example.miniodemo.dto.MergeRequestDto;
import org.example.miniodemo.service.MinioService;
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
import java.util.List;
import java.util.Map;

/**
 * 文件操作相关的API接口。
 * 包含文件列表、分片上传、文件合并、下载、删除等功能。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/minio")
public class FileController {

    private final MinioService minioService;
    private final MinioBucketConfig bucketConfig;

    /**
     * POST /init-buckets : 初始化应用所需的存储桶。
     * 在应用首次启动时调用此接口，可以自动创建配置中定义的存储桶。
     */
    @PostMapping("/init-buckets")
    public ResponseEntity<String> initBuckets() {
        try {
            if (!minioService.bucketExists(bucketConfig.getPrivateFiles())) {
                minioService.makeBucket(bucketConfig.getPrivateFiles());
            }
            if (!minioService.bucketExists(bucketConfig.getPublicAssets())) {
                minioService.makeBucket(bucketConfig.getPublicAssets());
                // 此处可以添加设置公开桶策略的逻辑
            }
            return ResponseEntity.ok("存储桶初始化成功！");
        } catch (Exception e) {
            log.error("存储桶初始化失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("存储桶初始化失败: " + e.getMessage());
        }
    }

    /**
     * GET /private/list : 获取私有存储桶中所有文件的列表。
     *
     * @return 文件的名称和大小列表。
     */
    @GetMapping("/private/list")
    public ResponseEntity<?> listPrivateFiles() {
        try {
            List<Map<String, Object>> fileList = minioService.listPrivateFiles();
            return ResponseEntity.ok(fileList);
        } catch (Exception e) {
            log.error("获取文件列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取文件列表失败: " + e.getMessage());
        }
    }

    // --- 私有文件手动分片上传接口 ---

    /**
     * POST /private/upload/chunk : 上传单个私有文件分片。
     *
     * @param file        文件分片数据
     * @param batchId     唯一批次ID
     * @param chunkNumber 分片序号
     * @return 上传结果
     */
    @PostMapping("/private/upload/chunk")
    public ResponseEntity<String> uploadPrivateChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("batchId") String batchId,
            @RequestParam("chunkNumber") Integer chunkNumber) {
        
        if (file.isEmpty() || batchId.isBlank()) {
            return ResponseEntity.badRequest().body("文件、批次ID或分片序号不能为空");
        }
        
        try {
            minioService.uploadChunk(file, batchId, chunkNumber);
            return ResponseEntity.ok("分片 " + chunkNumber + " 上传成功");
        } catch (Exception e) {
            log.error("分片上传失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * POST /private/upload/merge : 通知服务器合并指定批次的所有分片。
     *
     * @param mergeRequest 包含批次ID和最终文件名的请求体
     * @return 合并结果
     */
    @PostMapping("/private/upload/merge")
    public ResponseEntity<String> mergePrivateChunks(@RequestBody MergeRequestDto mergeRequest) {
        try {
            minioService.mergeChunks(mergeRequest.getBatchId(), mergeRequest.getFileName());
            return ResponseEntity.ok("文件合并成功: " + mergeRequest.getFileName());
        } catch (Exception e) {
            log.error("文件合并失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件合并失败: " + e.getMessage());
        }
    }

    /**
     * GET /private/download-url : 获取私有文件的预签名下载URL。
     *
     * @param fileName 文件名
     * @return 文件的预签名URL
     */
    @GetMapping("/private/download-url")
    public ResponseEntity<String> getPrivatePresignedDownloadUrl(@RequestParam("fileName") String fileName) {
        try {
            String url = minioService.getPresignedPrivateDownloadUrl(fileName);
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取 URL 失败: " + e.getMessage());
        }
    }

    /**
     * GET /private/download : 通过后端代理下载私有文件。
     *
     * @param fileName 文件名
     * @return 文件数据流
     */
    @GetMapping("/private/download")
    public ResponseEntity<Resource> downloadPrivateFile(@RequestParam("fileName") String fileName) {
        try {
            InputStream inputStream = minioService.downloadPrivateFile(fileName);
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
     * DELETE /private/delete : 删除一个私有文件。
     *
     * @param fileName 文件名
     * @return 删除结果
     */
    @DeleteMapping("/private/delete")
    public ResponseEntity<String> deletePrivateFile(@RequestParam("fileName") String fileName) {
        try {
            minioService.deletePrivateFile(fileName);
            return ResponseEntity.ok("文件删除成功: " + fileName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败: " + e.getMessage());
        }
    }

    // --- 公开图片上传接口 ---

    /**
     * POST /public/upload-image : 上传单个公开图片。
     *
     * @param file 图片文件
     * @return 图片的永久公开URL
     */
    @PostMapping("/public/upload-image")
    public ResponseEntity<?> uploadPublicImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件不能为空");
        }
        try {
            String url = minioService.uploadPublicImage(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("公开图片上传失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("上传失败: " + e.getMessage());
        }
    }
}
