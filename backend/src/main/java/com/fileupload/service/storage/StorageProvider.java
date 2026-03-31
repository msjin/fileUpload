package com.fileupload.service.storage;

import java.io.InputStream;

/**
 * 存储提供者接口
 * 支持阿里云 OSS、AWS S3、本地文件系统等
 */
public interface StorageProvider {

    /**
     * 存储类型名称
     */
    String getType();

    /**
     * 保存文件分片
     * @param bucket 桶
     * @param key 文件键
     * @param chunkIndex 分片索引
     * @param inputStream 分片数据流
     */
    void saveChunk(String bucket, String key, int chunkIndex, InputStream inputStream);

    /**
     * 合并所有分片为完整文件
     * @param bucket 桶
     * @param key 文件键
     * @param totalChunks 总分片数
     */
    void mergeChunks(String bucket, String key, int totalChunks);

    /**
     * 获取文件访问 URL
     * @param bucket 桶
     * @param key 文件键
     * @return 文件 URL
     */
    String getFileUrl(String bucket, String key);

    /**
     * 检查文件是否存在
     * @param bucket 桶
     * @param key 文件键
     * @return true-存在 false-不存在
     */
    boolean exists(String bucket, String key);

    /**
     * 删除文件
     * @param bucket 桶
     * @param key 文件键
     */
    void delete(String bucket, String key);

    /**
     * 删除分片
     * @param bucket 桶
     * @param key 文件键
     * @param chunkIndex 分片索引
     */
    void deleteChunk(String bucket, String key, int chunkIndex);
}
