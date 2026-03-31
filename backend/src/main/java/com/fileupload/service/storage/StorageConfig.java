package com.fileupload.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 存储配置
 */
@Configuration
public class StorageConfig {

    /**
     * 存储类型：local, aliyun, aws-s3 等
     */
    @Value("${storage.type:local}")
    private String type;

    public String getType() {
        return type;
    }
}
