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

@Slf4j
@RestController
@RequestMapping("/minio/private")
@RequiredArgsConstructor
public class PrivateFileController {

    private final PrivateFileService privateFileService;

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