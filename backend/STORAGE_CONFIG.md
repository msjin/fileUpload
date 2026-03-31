# 存储提供者配置指南

## 🎯 支持的存储类型

系统已抽象存储层，支持多种存储后端：

| 类型 | 标识 | 描述 |
|------|------|------|
| **本地存储** | `local` | 文件存储在服务器本地磁盘 |
| **阿里云 OSS** | `aliyun` | 阿里云对象存储服务 |
| ~~AWS S3~~ | `aws-s3` | (待实现) |
| ~~MinIO~~ | `minio` | (待实现) |

## ⚙️ 快速配置

### 方式一：使用本地存储（默认）

```yaml
# application.yml
storage:
  type: local
  
upload:
  storage-path: ./uploads  # 本地存储路径
```

**特点：**
- ✅ 无需额外配置
- ✅ 开发测试方便
- ❌ 不适合生产环境（扩展性差）

### 方式二：使用阿里云 OSS

#### 1. 修改配置文件

```yaml
# application.yml
storage:
  type: aliyun

aliyun:
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: YOUR_ACCESS_KEY_ID
    access-key-secret: YOUR_ACCESS_KEY_SECRET
    bucket-name: your-bucket-name
```

#### 2. 或使用环境变量（推荐）

```bash
export STORAGE_TYPE=aliyun
export ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
export ALIYUN_OSS_ACCESS_KEY_ID=your-key
export ALIYUN_OSS_ACCESS_KEY_SECRET=your-secret
export ALIYUN_OSS_BUCKET_NAME=your-bucket
```

#### 3. Docker Compose 配置

```yaml
services:
  backend:
    environment:
      - STORAGE_TYPE=aliyun
      - ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
      - ALIYUN_OSS_ACCESS_KEY_ID=${ALIYUN_OSS_ACCESS_KEY_ID}
      - ALIYUN_OSS_ACCESS_KEY_SECRET=${ALIYUN_OSS_ACCESS_KEY_SECRET}
      - ALIYUN_OSS_BUCKET_NAME=${ALIYUN_OSS_BUCKET_NAME}
```

## 📁 目录结构对比

### 本地存储
```
uploads/
├── chunks/           # 临时分片
│   └── {fileMd5}/
│       ├── 0
│       ├── 1
│       └── ...
└── files/            # 最终文件
    └── 2024/
        └── 01/
            └── 15/
                └── {uuid}_{filename}
```

### 阿里云 OSS
```
Bucket: your-bucket-name
├── chunks/           # 临时分片
│   └── {fileMd5}/
│       ├── 0
│       ├── 1
│       └── ...
└── files/            # 最终文件
    └── 2024/
        └── 01/
            └── 15/
                └── {uuid}_{filename}
```

## 🔧 切换存储提供商

### 步骤 1: 停止服务
```bash
docker-compose down
# 或 Ctrl+C 停止本地运行
```

### 步骤 2: 修改配置
编辑 `application.yml` 或环境变量

### 步骤 3: 启动服务
```bash
# 本地运行
mvn spring-boot:run

# Docker
docker-compose up -d
```

### 步骤 4: 验证配置
```bash
curl http://localhost:8080/api/upload/health
```

## 🏗️ 架构设计

### 核心接口
```java
public interface StorageProvider {
    void saveChunk(String bucket, String key, int chunkIndex, InputStream inputStream);
    void mergeChunks(String bucket, String key, int totalChunks);
    String getFileUrl(String bucket, String key);
    boolean exists(String bucket, String key);
    void delete(String bucket, String key);
    void deleteChunk(String bucket, String key, int chunkIndex);
}
```

### 工厂模式选择
```java
StorageProviderFactory factory = ...;
StorageProvider provider = factory.getProvider(); // 根据配置自动选择
```

### 工作流程
```
FileUploadService
    ↓
StorageProviderFactory
    ↓ (根据 storage.type 配置)
├─ LocalStorageProvider
├─ AliyunOssStorageProvider
└─ (其他 Provider...)
```

## 📊 存储方案对比

| 特性 | 本地存储 | 阿里云 OSS |
|------|---------|-----------|
| **成本** | 低（仅需磁盘） | 按量付费 |
| **扩展性** | 受单机限制 | 无限扩展 |
| **可靠性** | 依赖服务器 | 99.999999999% |
| **访问速度** | 快（内网） | CDN 加速 |
| **运维成本** | 高（需备份） | 低（托管） |
| **适用场景** | 开发测试 | 生产环境 |

## 🛠️ 自定义存储提供商

### 步骤 1: 实现接口

```java
@Component("custom")
public class CustomStorageProvider extends AbstractStorageProvider {
    
    @Override
    public String getType() {
        return "custom";
    }
    
    @Override
    protected void doSaveChunk(String key, int chunkIndex, InputStream inputStream) {
        // 你的实现
    }
    
    // ... 其他方法
}
```

### 步骤 2: 配置使用

```yaml
storage:
  type: custom
```

## 💡 最佳实践

### 开发环境
```yaml
storage:
  type: local
upload:
  storage-path: ./tmp/uploads
```

### 测试环境
```yaml
storage:
  type: local
upload:
  storage-path: /data/test-uploads
```

### 生产环境
```yaml
storage:
  type: aliyun
aliyun:
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    # 使用环境变量
```

### 混合部署（高级）
```java
// 小文件用本地存储，大文件用 OSS
if (fileSize < 10 * 1024 * 1024) { // < 10MB
    return storageProviderFactory.getProvider("local");
} else {
    return storageProviderFactory.getProvider("aliyun");
}
```

## ⚠️ 注意事项

### 本地存储
1. **定期清理**: 设置定时任务清理过期的临时分片
2. **磁盘监控**: 监控磁盘使用率，设置告警
3. **备份策略**: 重要文件需要定期备份
4. **权限控制**: 确保上传目录有正确的读写权限

### 阿里云 OSS
1. **费用控制**: 设置存储用量告警
2. **CDN 加速**: 配置 CDN 降低流量成本
3. **生命周期**: 设置分片自动过期规则
4. **访问控制**: 使用 RAM 子账号和最小权限原则
5. **跨区域复制**: 重要数据开启跨可用区复制

## 🔍 故障排查

### 问题 1: 切换到 OSS 后上传失败
**检查清单:**
- [ ] 确认 `storage.type=aliyun`
- [ ] 验证 OSS 凭证正确
- [ ] 检查 Bucket 是否存在
- [ ] 确认网络可访问 OSS Endpoint

**调试命令:**
```bash
# 测试 OSS 连通性
curl https://oss-cn-hangzhou.aliyuncs.com

# 查看应用日志
tail -f logs/application.log | grep "OSS"
```

### 问题 2: 本地存储空间不足
**解决方案:**
```bash
# 1. 查看当前存储大小
du -sh ./uploads

# 2. 清理过期分片
find ./uploads/chunks -type d -mtime +7 -exec rm -rf {} \;

# 3. 迁移到 OSS
export STORAGE_TYPE=aliyun
```

### 问题 3: 分片合并且失败
**可能原因:**
- 部分分片丢失
- 存储空间不足
- 网络问题（OSS）

**排查步骤:**
```bash
# 查看 Redis 中的分片计数
redis-cli GET "chunk:uploaded:{fileMd5}"

# 检查实际分片数量
ls -la ./uploads/chunks/{fileMd5}/
```

## 📈 性能优化建议

### 本地存储
- 使用 SSD 硬盘
- RAID 阵列提升可靠性
- 独立分区避免系统盘满载

### 阿里云 OSS
- 选择就近的 Region
- 开启 CDN 加速
- 使用分片上传优化大文件
- 内网访问节省流量费用

---

**最后更新**: 2024-01-15  
**文档版本**: v1.0
