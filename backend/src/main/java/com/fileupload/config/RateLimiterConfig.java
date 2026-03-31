package com.fileupload.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流器组件
 * 多维度限流：单用户频率限制、全局并发限制
 */
@Component
public class RateLimiterConfig {

    @Autowired
    private UploadProperties uploadProperties;

    /**
     * 用户级别的速率限制器 (按用户 ID)
     */
    private final ConcurrentHashMap<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();

    /**
     * 全局速率限制器
     */
    private RateLimiter globalRateLimiter;

    @PostConstruct
    public void init() {
        // 初始化全局限流器：每秒处理 rateLimit 个请求
        this.globalRateLimiter = RateLimiter.create(uploadProperties.getRateLimit());
    }

    /**
     * 获取或创建用户限流器
     */
    public RateLimiter getUserRateLimiter(String userId) {
        return userRateLimiters.computeIfAbsent(userId, 
            k -> RateLimiter.create(uploadProperties.getRateLimit()));
    }

    /**
     * 尝试获取全局许可
     * @return true-成功 false-失败
     */
    public boolean tryAcquireGlobal() {
        return globalRateLimiter.tryAcquire(0, TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试获取用户限流许可
     * @param userId 用户 ID
     * @return true-成功 false-失败
     */
    public boolean tryAcquireUser(String userId) {
        RateLimiter rateLimiter = getUserRateLimiter(userId);
        return rateLimiter.tryAcquire(0, TimeUnit.MILLISECONDS);
    }

    /**
     * 阻塞式获取许可 (带超时)
     * @param userId 用户 ID
     * @param timeout 超时时间 (毫秒)
     * @return true-成功 false-超时
     */
    public boolean acquireWithTimeout(String userId, long timeout) {
        RateLimiter userLimiter = getUserRateLimiter(userId);
        return userLimiter.tryAcquire(timeout, TimeUnit.MILLISECONDS);
    }
}
