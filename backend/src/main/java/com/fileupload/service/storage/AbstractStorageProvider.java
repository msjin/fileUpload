package com.fileupload.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.InputStream;

/**
 * 存储提供者抽象基类
 */
public abstract class AbstractStorageProvider implements StorageProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    /**
     * 存储路径（本地存储使用）
     */
    @Autowired(required = false)
    protected String storagePath = "./uploads";

    @Override
    public void saveChunk(String bucket, String key, int chunkIndex, InputStream inputStream) {
        try {
            doSaveChunk(key, chunkIndex, inputStream);
            log.debug("保存分片成功：{}-{}-{}", bucket, key, chunkIndex);
        } catch (Exception e) {
            log.error("保存分片失败：{}-{}-{}", bucket, key, chunkIndex, e);
            throw new RuntimeException("保存分片失败", e);
        }
    }

    @Override
    public void mergeChunks(String bucket, String key, int totalChunks) {
        try {
            // 从 Redis 获取文件名
            String fileNameKey = "upload:" + key;
            String fileName = (String) redisTemplate.opsForHash().get(fileNameKey, "fileName");
            
            doMergeChunks(key, fileName != null ? fileName : "unknown", totalChunks);
            log.debug("合并分片成功：{}-{}", bucket, key);
        } catch (Exception e) {
            log.error("合并分片失败：{}-{}", bucket, key, e);
            throw new RuntimeException("合并分片失败", e);
        }
    }

    @Override
    public String getFileUrl(String bucket, String key) {
        return doGetFileUrl(key);
    }

    @Override
    public boolean exists(String bucket, String key) {
        return doExists(key);
    }

    @Override
    public void delete(String bucket, String key) {
        doDelete(key);
        log.debug("删除文件成功：{}-{}", bucket, key);
    }

    @Override
    public void deleteChunk(String bucket, String key, int chunkIndex) {
        doDeleteChunk(key, chunkIndex);
    }

    /**
     * 保存分片的具体实现
     */
    protected abstract void doSaveChunk(String key, int chunkIndex, InputStream inputStream) throws Exception;

    /**
     * 合并分片的具体实现
     */
    protected abstract void doMergeChunks(String key, String fileName, int totalChunks) throws Exception;

    /**
     * 获取文件 URL 的具体实现
     */
    protected abstract String doGetFileUrl(String key);

    /**
     * 检查文件是否存在的具体实现
     */
    protected abstract boolean doExists(String key);

    /**
     * 删除文件的具体实现
     */
    protected abstract void doDelete(String key);

    /**
     * 删除分片的具体实现
     */
    protected abstract void doDeleteChunk(String key, int chunkIndex);
}
