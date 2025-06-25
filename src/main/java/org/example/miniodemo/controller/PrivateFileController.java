package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;

/**
 * 专用于处理私有文件（Private Files）相关操作的API控制器。
 * <p>
 * “私有文件”是指存储在受限访问存储桶中的对象，必须通过预签名URL或后端代理才能访问，
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
     * GET /list : 获取私有存储桶中所有文件的列表。
     * <p>
     * 此接口会过滤掉用于分片上传的临时文件，只返回最终合并完成的完整文件。
     *
     * @return {@link ResponseEntity} 包含文件信息列表的响应实体。每个文件信息是一个包含
     *         "name" (对象名) 和 "size" (文件大小) 的Map。
     *         成功时返回列表和HTTP 200，失败时返回错误信息和HTTP 500。
     */
    @GetMapping("/list")
    public ResponseEntity<?> listPrivateFiles() {
        try {
            List<Map<String, Object>> fileList = privateFileService.listPrivateFiles();
            return ResponseEntity.ok(fileList);
        } catch (Exception e) {
            log.error("获取文件列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * POST /upload/chunk : 上传单个文件分片。
     * <p>
     * 这是分片上传流程的第二步，前端将文件切分后，会为每个分片调用此接口。
     *
     * @param file        由multipart/form-data请求体承载的文件分片数据。
     * @param batchId     唯一标识本次完整文件上传的批次ID，由前端生成。
     * @param chunkNumber 当前分片的序号（从0开始）。
     * @return {@link ResponseEntity} 包含操作结果字符串的响应实体。
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<String> uploadPrivateChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("batchId") String batchId,
            @RequestParam("chunkNumber") Integer chunkNumber) {
        
        if (file.isEmpty() || batchId.isBlank()) {
            return ResponseEntity.badRequest().body("文件、批次ID或分片序号不能为空");
        }
        
        try {
            privateFileService.uploadChunk(file, batchId, chunkNumber);
            return ResponseEntity.ok("分片 " + chunkNumber + " 上传成功");
        } catch (Exception e) {
            log.error("分片上传失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * POST /upload/merge : 通知服务器合并指定批次的所有分片。
     * <p>
     * 这是分片上传流程的最后一步。当前端所有分片都成功调用 {@code /upload/chunk} 后，
     * 调用此接口来触发服务器端的文件合并操作。
     *
     * @param mergeRequest 包含批次ID (batchId) 和最终文件名 (fileName) 的请求体。
     * @return {@link ResponseEntity} 包含操作结果字符串的响应实体。
     */
    @PostMapping("/upload/merge")
    public ResponseEntity<String> mergePrivateChunks(@RequestBody MergeRequestDto mergeRequest) {
        try {
            privateFileService.mergeChunks(mergeRequest.getBatchId(), mergeRequest.getFileName());
            return ResponseEntity.ok("文件合并成功: " + mergeRequest.getFileName());
        } catch (Exception e) {
            log.error("文件合并失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件合并失败: " + e.getMessage());
        }
    }

    /**
     * GET /download-url : 获取私有文件的预签名下载URL。
     * <p>
     * 生成一个有时间限制（例如15分钟）的URL，客户端（如浏览器）可以使用此URL直接从MinIO下载文件，
     * 而无需通过应用服务器代理。这是推荐的高效下载方式。
     *
     * @param fileName 需要下载的文件的完整对象名。
     * @return {@link ResponseEntity} 包含预签名URL字符串的响应实体。
     */
    @GetMapping("/download-url")
    public ResponseEntity<String> getPrivatePresignedDownloadUrl(@RequestParam("fileName") String fileName) {
        try {
            String url = privateFileService.getPresignedPrivateDownloadUrl(fileName);
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取 URL 失败: " + e.getMessage());
        }
    }

    /**
     * GET /download : 通过后端服务器代理下载私有文件。
     * <p>
     * 这种方式会将文件数据从MinIO流经本应用服务器，再转发给客户端。
     * 它会占用应用服务器的带宽和内存，适用于需要对下载过程进行额外控制（如权限校验、日志记录）的场景，
     * 但在性能上不如预签名URL。
     *
     * @param fileName 需要下载的文件的完整对象名。
     * @return {@link ResponseEntity} 包含文件数据流的 {@link Resource} 响应实体。
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
     * DELETE /delete : 删除一个私有文件。
     *
     * @param fileName 需要删除的文件的完整对象名。
     * @return {@link ResponseEntity} 包含操作结果字符串的响应实体。
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePrivateFile(@RequestParam("fileName") String fileName) {
        try {
            privateFileService.deletePrivateFile(fileName);
            return ResponseEntity.ok("文件删除成功: " + fileName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败: " + e.getMessage());
        }
    }
} 