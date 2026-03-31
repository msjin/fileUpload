package com.fileupload.controller;

import com.fileupload.config.RateLimiterConfig;
import com.fileupload.dto.ChunkUploadRequest;
import com.fileupload.dto.FileUploadRequest;
import com.fileupload.dto.UploadResponse;
import com.fileupload.exception.FileUploadException;
import com.fileupload.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传控制器
 * 提供以下接口：
 * 1. POST /upload/health - 健康检查
 * 2. POST /upload/check - 检查文件是否存在 (秒传)
 * 3. POST /upload/init - 初始化上传
 * 4. POST /upload/chunk - 上传分片
 * 5. GET /upload/chunks/{fileMd5} - 获取已上传分片 (断点续传)
 */
@RestController
@RequestMapping("/upload")
@CrossOrigin(origins = "*") // 允许跨域，生产环境应该限制具体域名
public class FileUploadController {

    @Autowired
    private FileUploadService uploadService;

    @Autowired
    private RateLimiterConfig rateLimiterConfig;

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("message", "File Upload Service is running");
        return result;
    }

    /**
     * 检查文件是否存在 (秒传)
     * @param fileMd5 文件 MD5
     * @return 上传响应
     */
    @PostMapping("/check")
    public ResponseEntity<UploadResponse> checkFile(@RequestParam String fileMd5) {
        try {
            UploadResponse response = uploadService.checkFileExists(fileMd5);
            if (response != null) {
                return ResponseEntity.ok(response);
            }
            
            // 文件不存在
            return ResponseEntity.ok(UploadResponse.builder()
                    .success(false)
                    .message("文件不存在，请继续上传")
                    .needContinue(true)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                UploadResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 初始化上传
     * @param request 上传请求
     * @return 上传响应
     */
    @PostMapping("/init")
    public ResponseEntity<UploadResponse> initUpload(@RequestBody FileUploadRequest request) {
        try {
            // 限流检查
            if (!rateLimiterConfig.tryAcquireUser(request.getUserId())) {
                throw new FileUploadException.RateLimitExceededException("上传频率过高，请稍后重试");
            }
            
            // 检查每日上传次数
            uploadService.checkDailyLimit(request.getUserId());
            
            // 初始化上传
            UploadResponse response = uploadService.initUpload(request);
            return ResponseEntity.ok(response);
        } catch (FileUploadException e) {
            return ResponseEntity.badRequest().body(
                UploadResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build()
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                UploadResponse.builder()
                    .success(false)
                    .message("服务器错误：" + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 上传分片
     * @param fileMd5 文件 MD5
     * @param chunkIndex 分片索引
     * @param totalChunks 总分片数
     * @param chunkSize 分片大小
     * @param userId 用户 ID
     * @param chunkFile 分片文件
     * @return 上传响应
     */
    @PostMapping("/chunk")
    public ResponseEntity<UploadResponse> uploadChunk(
            @RequestParam String fileMd5,
            @RequestParam Integer chunkIndex,
            @RequestParam Integer totalChunks,
            @RequestParam Long chunkSize,
            @RequestParam String userId,
            @RequestParam MultipartFile chunkFile) {
        try {
            // 限流检查
            if (!rateLimiterConfig.tryAcquireUser(userId)) {
                throw new FileUploadException.RateLimitExceededException("上传频率过高，请稍后重试");
            }
            
            // 构建请求对象
            ChunkUploadRequest request = ChunkUploadRequest.builder()
                    .fileMd5(fileMd5)
                    .chunkIndex(chunkIndex)
                    .totalChunks(totalChunks)
                    .chunkSize(chunkSize)
                    .userId(userId)
                    .build();
            
            // 上传分片
            UploadResponse response = uploadService.uploadChunk(request, chunkFile);
            return ResponseEntity.ok(response);
        } catch (FileUploadException e) {
            return ResponseEntity.badRequest().body(
                UploadResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build()
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                UploadResponse.builder()
                    .success(false)
                    .message("服务器错误：" + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 获取已上传的分片 (用于断点续传)
     * @param fileMd5 文件 MD5
     * @param totalChunks 总分片数
     * @return 已上传的分片索引列表
     */
    @GetMapping("/chunks/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getUploadedChunks(
            @PathVariable String fileMd5,
            @RequestParam Integer totalChunks) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(fileMd5, totalChunks);
            result.put("success", true);
            result.put("uploadedChunks", uploadedChunks);
            result.put("totalChunks", totalChunks);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
