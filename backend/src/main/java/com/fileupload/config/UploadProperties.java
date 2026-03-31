package com.fileupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /**
     * 分片大小 (字节) 默认 2MB
     */
    private Long chunkSize = 2097152L;

    /**
     * 单文件最大大小 (字节) 默认 10GB
     */
    private Long maxFileSize = 10737418240L;

    /**
     * 每日上传次数限制
     */
    private Integer dailyLimit = 100;

    /**
     * 上传频率限制 (次/分钟)
     */
    private Integer rateLimit = 10;

    /**
     * 本地存储路径
     */
    private String storagePath = "./uploads";
}
