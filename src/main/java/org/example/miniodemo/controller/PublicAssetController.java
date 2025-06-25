package org.example.miniodemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.service.PublicAssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/minio/public")
@RequiredArgsConstructor
public class PublicAssetController {

    private final PublicAssetService publicAssetService;

    /**
     * GET /list : 获取公共存储桶中所有文件的列表。
     * @return 文件信息列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listPublicFiles() {
        try {
            return ResponseEntity.ok(publicAssetService.listPublicFiles());
        } catch (Exception e) {
            log.error("获取公共文件列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    /**
     * POST /upload : 上传一个公开的图片文件。
     * @param file 上传的文件
     * @return 包含文件公开URL的响应实体
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadPublicImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = publicAssetService.uploadPublicImage(file);
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            log.error("上传公共图片失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("上传失败");
        }
    }

    /**
     * DELETE /delete : 删除一个公共文件。
     * @param fileName 文件名
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePublicFile(@RequestParam("fileName") String fileName) {
        try {
            publicAssetService.deletePublicFile(fileName);
            return ResponseEntity.ok("文件删除成功: " + fileName);
        } catch (Exception e) {
            log.error("删除公共文件失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败: " + e.getMessage());
        }
    }
} 