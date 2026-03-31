package com.fileupload.service.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云 OSS 存储实现
 */
@Component("aliyun")
public class AliyunOssStorageProvider extends AbstractStorageProvider {

    @Value("${aliyun.oss.endpoint:oss-cn-hangzhou.aliyuncs.com}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name:default-bucket}")
    private String bucketName;

    @Override
    public String getType() {
        return "aliyun";
    }

    @Override
    protected void doSaveChunk(String key, int chunkIndex, InputStream inputStream) throws Exception {
        OSS ossClient = createOSSClient();
        try {
            String objectKey = buildChunkKey(key, chunkIndex);
            
            // 使用追加上传的方式保存分片
            AppendObjectRequest appendObjectRequest = new AppendObjectRequest(bucketName, objectKey, inputStream);
            appendObjectRequest.setPosition(0L);
            
            ossClient.appendObject(appendObjectRequest);
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    protected void doMergeChunks(String key, String fileName, int totalChunks) throws Exception {
        OSS ossClient = createOSSClient();
        try {
            String finalObjectKey = buildFinalKey(fileName);
            
            // 收集所有分片
            List<PartETag> partETags = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                String chunkKey = buildChunkKey(key, i);
                
                // 获取分片 ETag
                OSSObject object = ossClient.getObject(bucketName, chunkKey);
                String eTag = object.getObjectMetadata().getETag();
                partETags.add(new PartETag(i + 1, eTag));
                object.close();
            }
            
            // 使用分片上传的合并功能
            InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(
                new InitiateMultipartUploadRequest(bucketName, finalObjectKey)
            );
            
            CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(
                bucketName, finalObjectKey, result.getUploadId(), partETags
            );
            
            ossClient.completeMultipartUpload(completeRequest);
            
            // 删除临时分片
            for (int i = 0; i < totalChunks; i++) {
                ossClient.deleteObject(bucketName, buildChunkKey(key, i));
            }
            
            // 存储文件 URL 到 Redis
            String fileUrl = getFileUrlFromKey(finalObjectKey);
            redisTemplate.opsForValue().set("file:" + key, fileUrl);
            
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    protected String doGetFileUrl(String key) {
        Object path = redisTemplate.opsForValue().get("file:" + key);
        if (path != null) {
            return path.toString();
        }
        return null;
    }

    @Override
    protected boolean doExists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("file:" + key));
    }

    @Override
    protected void doDelete(String key) {
        OSS ossClient = createOSSClient();
        try {
            Object objectKeyObj = redisTemplate.opsForValue().get("file:" + key);
            if (objectKeyObj != null) {
                ossClient.deleteObject(bucketName, objectKeyObj.toString());
                redisTemplate.delete("file:" + key);
            }
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    protected void doDeleteChunk(String key, int chunkIndex) {
        OSS ossClient = createOSSClient();
        try {
            ossClient.deleteObject(bucketName, buildChunkKey(key, chunkIndex));
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 创建 OSS 客户端
     */
    private OSS createOSSClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    /**
     * 构建分片 Object Key
     */
    private String buildChunkKey(String key, int chunkIndex) {
        return "chunks/" + key + "/" + chunkIndex;
    }

    /**
     * 构建最终文件 Key
     */
    private String buildFinalKey(String fileName) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateDir = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return "files/" + dateDir + "/" + java.util.UUID.randomUUID() + "_" + fileName;
    }

    /**
     * 从 Object Key 生成访问 URL
     */
    private String getFileUrlFromKey(String objectKey) {
        // 返回相对路径，实际使用时可以拼接 CDN 域名
        return "/" + objectKey;
    }
}
