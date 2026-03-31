package com.fileupload.service.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存储服务工厂
 * 根据配置动态选择存储提供者
 */
@Component
public class StorageProviderFactory {

    @Autowired
    private List<StorageProvider> providers;

    @Autowired
    private StorageConfig storageConfig;

    /**
     * 获取配置的存储提供者
     */
    public StorageProvider getProvider() {
        String type = storageConfig.getType();
        
        return providers.stream()
                .filter(p -> p.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> 
                    new IllegalArgumentException("未找到存储提供者：" + type));
    }

    /**
     * 根据类型获取存储提供者
     */
    public StorageProvider getProvider(String type) {
        return providers.stream()
                .filter(p -> p.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> 
                    new IllegalArgumentException("未找到存储提供者：" + type));
    }

    /**
     * 获取所有可用的存储提供者
     */
    public List<StorageProvider> getAllProviders() {
        return providers;
    }
}
