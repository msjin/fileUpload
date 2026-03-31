package com.fileupload.service;

import com.fileupload.config.UploadProperties;
import com.fileupload.dto.ChunkUploadRequest;
import com.fileupload.dto.FileUploadRequest;
import com.fileupload.dto.UploadResponse;
import com.fileupload.exception.FileUploadException;
import com.fileupload.service.storage.StorageProvider;
import com.fileupload.service.storage.StorageProviderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 文件上传服务
 * 核心功能：
 * 1. MD5 秒传检测
 * 2. 分片上传
 * 3. 断点续传
 * 4. 限流控制
 */
@Service
public class FileUploadService {

    @Autowired
    private UploadProperties uploadProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StorageProviderFactory storageProviderFactory;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 检查文件是否存在 (秒传)
     * @param fileMd5 文件 MD5
     * @return 文件信息
     */
    public UploadResponse checkFileExists(String fileMd5) {
        String redisKey = "file:md5:" + fileMd5;
        Object fileInfo = redisTemplate.opsForValue().get(redisKey);
        
        if (fileInfo != null) {
            // 文件已存在，返回秒传成功
            return UploadResponse.builder()
                    .success(true)
                    .message("文件已存在，秒传成功")
                    .fileMd5(fileMd5)
                    .fileUrl(fileInfo.toString())
                    .needContinue(false)
                    .build();
        }
        
        return null;
    }

    /**
     * 初始化上传
     * @param request 上传请求
     * @return 上传响应
     */
    public UploadResponse initUpload(FileUploadRequest request) {
        // 文件大小校验
        if (request.getFileSize() > uploadProperties.getMaxFileSize()) {
            throw new FileUploadException.FileSizeExceededException(
                "文件大小超过限制：" + uploadProperties.getMaxFileSize() / 1024 / 1024 / 1024 + "GB");
        }

        // 计算总分片数
        long totalChunks = (long) Math.ceil((double) request.getFileSize() / uploadProperties.getChunkSize());
        
        // 生成上传会话 ID
        String uploadId = UUID.randomUUID().toString();
        String redisKey = "upload:" + uploadId;
        
        // 保存上传元数据到 Redis
        redisTemplate.opsForHash().put(redisKey, "fileMd5", request.getFileMd5());
        redisTemplate.opsForHash().put(redisKey, "fileName", request.getFileName());
        redisTemplate.opsForHash().put(redisKey, "fileSize", String.valueOf(request.getFileSize()));
        redisTemplate.opsForHash().put(redisKey, "contentType", request.getContentType());
        redisTemplate.opsForHash().put(redisKey, "userId", request.getUserId());
        redisTemplate.opsForHash().put(redisKey, "totalChunks", String.valueOf(totalChunks));
        redisTemplate.opsForHash().put(redisKey, "uploadedChunks", "0");
        redisTemplate.opsForHash().put(redisKey, "status", "uploading");
        
        // 设置过期时间 7 天
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
        
        // 记录每日上传次数
        incrementDailyCount(request.getUserId());
        
        return UploadResponse.builder()
                .success(true)
                .message("初始化成功，请开始上传分片")
                .needContinue(true)
                .uploadedChunks(0)
                .totalChunks((int) totalChunks)
                .build();
    }

    /**
     * 上传分片
     * @param chunkRequest 分片请求
     * @param chunkFile 分片文件
     * @return 上传响应
     */
    public UploadResponse uploadChunk(ChunkUploadRequest chunkRequest, MultipartFile chunkFile) 
            throws IOException {
        
        String uploadId = generateUploadId(chunkRequest.getFileMd5(), chunkRequest.getUserId());
        String redisKey = "upload:" + uploadId;
        
        // 获取上传元数据
        String totalChunksStr = (String) redisTemplate.opsForHash().get(redisKey, "totalChunks");
        Integer totalChunks = Integer.parseInt(totalChunksStr);
        
        // 验证分片序号
        if (chunkRequest.getChunkIndex() >= totalChunks) {
            throw new FileUploadException.ChunkUploadFailed("分片序号超出范围");
        }
        
        // 使用存储提供者保存分片
        StorageProvider storageProvider = storageProviderFactory.getProvider();
        storageProvider.saveChunk("default", chunkRequest.getFileMd5(), 
                                 chunkRequest.getChunkIndex(), chunkFile.getInputStream());
        
        // 更新已上传分片数 (使用 Redis INCR 原子操作)
        String uploadedKey = "chunk:uploaded:" + chunkRequest.getFileMd5();
        Long uploadedChunks = redisTemplate.opsForValue().increment(uploadedKey);
        
        // 设置过期时间
        redisTemplate.expire(uploadedKey, 7, TimeUnit.DAYS);
        
        // 检查是否所有分片都已上传完成
        if (uploadedChunks >= totalChunks) {
            // 合并分片
            String fileUrl = mergeChunks(chunkRequest.getFileMd5(), 
                                        (String) redisTemplate.opsForHash().get(redisKey, "fileName"),
                                        (String) redisTemplate.opsForHash().get(redisKey, "contentType"));
            
            // 清理临时分片
            cleanupChunks(chunkRequest.getFileMd5());
            
            // 更新 Redis 状态
            redisTemplate.opsForHash().put(redisKey, "status", "completed");
            redisTemplate.opsForHash().put(redisKey, "fileUrl", fileUrl);
            
            // 记录 MD5 -> URL 映射 (用于秒传)
            redisTemplate.opsForValue().set("file:md5:" + chunkRequest.getFileMd5(), fileUrl, 30, TimeUnit.DAYS);
            
            return UploadResponse.builder()
                    .success(true)
                    .message("文件上传完成")
                    .fileUrl(fileUrl)
                    .fileMd5(chunkRequest.getFileMd5())
                    .needContinue(false)
                    .uploadedChunks(totalChunks)
                    .totalChunks(totalChunks)
                    .build();
        }
        
        return UploadResponse.builder()
                .success(true)
                .message("分片上传成功")
                .needContinue(true)
                .uploadedChunks(Math.toIntExact(uploadedChunks))
                .totalChunks(totalChunks)
                .build();
    }

    /**
     * 获取已上传的分片信息 (用于断点续传)
     * @param fileMd5 文件 MD5
     * @param totalChunks 总分片数
     * @return 已上传的分片索引列表
     */
    public java.util.List<Integer> getUploadedChunks(String fileMd5, int totalChunks) {
        String uploadedKey = "chunk:uploaded:" + fileMd5;
        // 实际场景中应该查询 Redis Set 记录所有已上传的分片索引
        // 这里简化处理
        return new java.util.ArrayList<>();
    }

    /**
     * 检查每日上传次数限制
     * @param userId 用户 ID
     */
    public void checkDailyLimit(String userId) {
        String key = "upload:daily:" + LocalDate.now().format(DATE_FORMATTER) + ":" + userId;
        Long count = (Long) redisTemplate.opsForValue().get(key);
        
        if (count != null && count >= uploadProperties.getDailyLimit()) {
            throw new FileUploadException.RateLimitExceededException(
                "今日上传次数已达上限：" + uploadProperties.getDailyLimit());
        }
    }

    /**
     * 增加每日上传次数
     * @param userId 用户 ID
     */
    private void incrementDailyCount(String userId) {
        String key = "upload:daily:" + LocalDate.now().format(DATE_FORMATTER) + ":" + userId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.DAYS);
    }

    /**
     * 生成分片存储目录
     */
    private String getChunkDir(String fileMd5) {
        return uploadProperties.getStoragePath() + "/chunks/" + fileMd5;
    }

    /**
     * 生成上传 ID
     */
    private String generateUploadId(String fileMd5, String userId) {
        return fileMd5 + ":" + userId;
    }

    /**
     * 合并分片
     */
    private String mergeChunks(String fileMd5, String fileName, String contentType) throws IOException {
        // 使用存储提供者合并分片
        StorageProvider storageProvider = storageProviderFactory.getProvider();
        
        // 获取总分片数
        String uploadedKey = "chunk:uploaded:" + fileMd5;
        Integer totalChunks = Math.toIntExact(
            (Long) redisTemplate.opsForValue().get(uploadedKey)
        );
        
        // 调用存储提供者的合并方法
        storageProvider.mergeChunks("default", fileMd5, totalChunks);
        
        // 返回文件访问路径
        return storageProvider.getFileUrl("default", fileMd5);
    }

    /**
     * 清理临时分片
     */
    private void cleanupChunks(String fileMd5) throws IOException {
        StorageProvider storageProvider = storageProviderFactory.getProvider();
        
        // 获取总分片数
        String uploadedKey = "chunk:uploaded:" + fileMd5;
        Long totalChunksLong = (Long) redisTemplate.opsForValue().get(uploadedKey);
        if (totalChunksLong == null) {
            return;
        }
        
        int totalChunks = totalChunksLong.intValue();
        
        // 删除所有分片
        for (int i = 0; i < totalChunks; i++) {
            storageProvider.deleteChunk("default", fileMd5, i);
        }
    }
}
