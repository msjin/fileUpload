# 存储层抽象实现总结

## ✅ 已完成功能

### 核心架构
```
StorageProvider (接口)
    ├── AbstractStorageProvider (抽象基类)
    │   ├── LocalStorageProvider (本地存储)
    │   └── AliyunOssStorageProvider (阿里云 OSS)
    └── StorageProviderFactory (工厂类)
        └── StorageConfig (配置类)
```

### 文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `StorageProvider.java` | 接口 | 存储提供者标准接口 |
| `AbstractStorageProvider.java` | 抽象类 | 通用逻辑复用 |
| `LocalStorageProvider.java` | 实现 | 本地文件系统存储 |
| `AliyunOssStorageProvider.java` | 实现 | 阿里云 OSS 对象存储 |
| `StorageConfig.java` | 配置 | 存储类型配置 |
| `StorageProviderFactory.java` | 工厂 | 动态选择存储提供者 |

### 接口方法

```java
public interface StorageProvider {
    // 保存分片
    void saveChunk(String bucket, String key, int chunkIndex, InputStream inputStream);
    
    // 合并分片
    void mergeChunks(String bucket, String key, int totalChunks);
    
    // 获取文件 URL
    String getFileUrl(String bucket, String key);
    
    // 检查文件是否存在
    boolean exists(String bucket, String key);
    
    // 删除文件
    void delete(String bucket, String key);
    
    // 删除分片
    void deleteChunk(String bucket, String key, int chunkIndex);
}
```

## 🔧 使用方式

### 1. 配置文件切换

**本地存储（默认）：**
```yaml
storage:
  type: local
  
upload:
  storage-path: ./uploads
```

**阿里云 OSS：**
```yaml
storage:
  type: aliyun

aliyun:
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: YOUR_KEY
    access-key-secret: YOUR_SECRET
    bucket-name: your-bucket
```

**环境变量方式：**
```bash
export STORAGE_TYPE=aliyun
export ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
export ALIYUN_OSS_ACCESS_KEY_ID=your-key
export ALIYUN_OSS_ACCESS_KEY_SECRET=your-secret
export ALIYUN_OSS_BUCKET_NAME=your-bucket
```

### 2. 代码调用

```java
@Autowired
private StorageProviderFactory storageProviderFactory;

// 获取配置的存储提供者
StorageProvider provider = storageProviderFactory.getProvider();

// 或者按类型获取
StorageProvider aliyunProvider = storageProviderFactory.getProvider("aliyun");
StorageProvider localProvider = storageProviderFactory.getProvider("local");

// 使用存储提供者
provider.saveChunk("default", fileMd5, chunkIndex, inputStream);
provider.mergeChunks("default", fileMd5, totalChunks);
String url = provider.getFileUrl("default", fileMd5);
```

## 🎯 设计优势

### 1. 开闭原则
- ✅ 对扩展开放：新增存储只需实现接口
- ✅ 对修改关闭：不影响现有代码

### 2. 依赖倒置
- ✅ FileUploadService 依赖抽象接口
- ✅ 不直接依赖具体实现

### 3. 单一职责
- ✅ 每个 Provider 只负责一种存储
- ✅ Factory 只负责创建
- ✅ Config 只负责配置

### 4. 策略模式
- ✅ 运行时动态切换存储策略
- ✅ 可根据文件大小、用户等维度选择

## 📊 实现对比

### LocalStorageProvider

**优点：**
- 简单直接，无需额外服务
- 开发测试方便
- 无网络延迟

**缺点：**
- 受单机容量限制
- 需要自己备份
- 扩展性差

**适用场景：**
- 开发环境
- 测试环境
- 小规模私有部署

### AliyunOssStorageProvider

**优点：**
- 无限容量扩展
- 高可靠性（99.999999999%）
- CDN 加速访问
- 按需付费

**缺点：**
- 需要网络访问
- 有流量费用
- 配置相对复杂

**适用场景：**
- 生产环境
- 大规模用户
- 需要高可用

## 🚀 扩展示例

### 添加 AWS S3 支持

```java
@Component("aws-s3")
public class AwsS3StorageProvider extends AbstractStorageProvider {
    
    @Value("${aws.s3.region}")
    private String region;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Override
    public String getType() {
        return "aws-s3";
    }
    
    @Override
    protected void doSaveChunk(String key, int chunkIndex, InputStream inputStream) {
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
            .withRegion(region)
            .build();
        
        String objectKey = "chunks/" + key + "/" + chunkIndex;
        s3Client.putObject(bucketName, objectKey, inputStream, null);
    }
    
    // ... 其他方法实现
}
```

**配置：**
```yaml
storage:
  type: aws-s3

aws:
  s3:
    region: us-east-1
    bucket-name: my-bucket
```

### 添加 MinIO 支持

```java
@Component("minio")
public class MinioStorageProvider extends AbstractStorageProvider {
    
    @Value("${minio.endpoint}")
    private String endpoint;
    
    @Value("${minio.access-key}")
    private String accessKey;
    
    @Value("${minio.secret-key}")
    private String secretKey;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Override
    public String getType() {
        return "minio";
    }
    
    @Override
    protected void doSaveChunk(String key, int chunkIndex, InputStream inputStream) {
        MinioClient client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        
        String objectKey = "chunks/" + key + "/" + chunkIndex;
        client.putObject(PutObjectArgs.builder()
            .bucket(bucketName)
            .object(objectKey)
            .stream(inputStream, -1, 10485760)
            .build());
    }
    
    // ... 其他方法实现
}
```

## 🔄 迁移方案

### 本地 → OSS 迁移

**步骤 1: 准备阶段**
```bash
# 1. 创建 OSS Bucket
# 2. 配置 RAM 权限
# 3. 测试连通性
```

**步骤 2: 双写模式（可选）**
```java
public void saveChunk(...) {
    // 同时写入本地和 OSS
    localStorage.saveChunk(...);
    ossStorage.saveChunk(...);
}
```

**步骤 3: 切换读取**
```yaml
# 修改配置
storage:
  type: aliyun
```

**步骤 4: 验证清理**
```bash
# 验证上传下载正常
# 清理本地存储
rm -rf ./uploads/*
```

## ⚠️ 注意事项

### 1. 数据一致性
- Redis 记录必须与存储同步
- 失败时需要回滚或重试

### 2. 错误处理
```java
try {
    provider.saveChunk(...);
} catch (Exception e) {
    log.error("保存失败", e);
    // 清理已上传的分片
    rollback(fileMd5, currentIndex);
    throw new FileUploadException("上传失败");
}
```

### 3. 性能考虑
- 本地存储：注意磁盘 IO 瓶颈
- OSS 存储：注意网络延迟和带宽

### 4. 成本控制
- OSS 流量费用可能较高
- 建议开启 CDN 加速
- 设置生命周期规则自动清理

## 📈 性能指标

### LocalStorage
- 写入速度：~100MB/s (SSD)
- 读取速度：~200MB/s (SSD)
- 延迟：< 1ms

### AliyunOSS
- 写入速度：取决于带宽
- 读取速度：CDN 加速后 ~50MB/s
- 延迟：50-200ms

## 🎨 最佳实践

### 开发环境
```yaml
storage:
  type: local
upload:
  storage-path: /tmp/uploads
logging:
  level:
    com.fileupload.service.storage: DEBUG
```

### 生产环境
```yaml
storage:
  type: aliyun
aliyun:
  oss:
    # 使用内网 Endpoint
    endpoint: oss-cn-hangzhou-internal.aliyuncs.com
# 开启 CDN
cdn:
  enabled: true
  domain: cdn.example.com
```

### 混合部署
```java
@Configuration
public class StorageStrategy {
    
    @Autowired
    private StorageProviderFactory factory;
    
    public StorageProvider selectProvider(long fileSize) {
        // 小文件本地，大文件 OSS
        if (fileSize < 50 * 1024 * 1024) {
            return factory.getProvider("local");
        } else {
            return factory.getProvider("aliyun");
        }
    }
}
```

## 🔍 监控指标

### 关键指标
- 上传成功率
- 平均上传时间
- 存储空间使用率
- 分片清理及时性

### 告警规则
```yaml
alerts:
  - name: 上传失败率过高
    condition: failure_rate > 5%
    
  - name: 存储空间不足
    condition: disk_usage > 80%
    
  - name: OSS 费用异常
    condition: daily_cost > threshold
```

---

**实现完成！** 🎉

现在系统支持：
✅ 本地文件系统存储
✅ 阿里云 OSS 对象存储
✅ 可轻松扩展其他存储服务
✅ 配置化切换，无需改代码
✅ 统一的错误处理和日志

详细文档请查看：[STORAGE_CONFIG.md](./STORAGE_CONFIG.md)
