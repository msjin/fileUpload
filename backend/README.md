# 文件上传后端服务

## 核心功能

### 1. 超大文件支持
- **分片上传**：将大文件切割成固定大小的分片（默认 2MB）
- **断点续传**：记录已上传的分片，网络中断后可继续
- **内存优化**：每个分片独立处理，避免大文件占用过多内存

### 2. 重复文件去重
- **MD5 秒传**：计算文件 MD5 摘要，相同文件只存储一次
- **哈希检测**：上传前检查文件是否已存在，存在则直接返回

### 3. 多维度限流
- **单文件大小限制**：最大 10GB（可配置）
- **每日上传次数**：每用户每天最多 100 次（可配置）
- **上传频率限制**：每用户每分钟最多 10 次请求（可配置）
- **全局限流**：使用 Guava RateLimiter 控制并发

## API 接口

### 1. 健康检查
```http
GET /api/upload/health
```

响应示例：
```json
{
  "status": "UP",
  "message": "File Upload Service is running"
}
```

### 2. 检查文件是否存在（秒传）
```http
POST /api/upload/check?fileMd5={fileMd5}
```

参数：
- `fileMd5`: 文件的 MD5 摘要

响应示例（文件存在）：
```json
{
  "success": true,
  "message": "文件已存在，秒传成功",
  "fileMd5": "abc123...",
  "fileUrl": "/api/upload/files/2024/01/15/file.txt",
  "needContinue": false
}
```

### 3. 初始化上传
```http
POST /api/upload/init
Content-Type: application/json

{
  "fileMd5": "abc123...",
  "fileName": "test.mp4",
  "fileSize": 10485760,
  "contentType": "video/mp4",
  "userId": "user123"
}
```

响应示例：
```json
{
  "success": true,
  "message": "初始化成功，请开始上传分片",
  "needContinue": true,
  "uploadedChunks": 0,
  "totalChunks": 5
}
```

### 4. 上传分片
```http
POST /api/upload/chunk
Content-Type: multipart/form-data

Parameters:
- fileMd5: 文件 MD5
- chunkIndex: 分片索引（从 0 开始）
- totalChunks: 总分片数
- chunkSize: 分片大小
- userId: 用户 ID
- chunkFile: 分片文件

```

响应示例（未完成）：
```json
{
  "success": true,
  "message": "分片上传成功",
  "needContinue": true,
  "uploadedChunks": 3,
  "totalChunks": 5
}
```

响应示例（已完成）：
```json
{
  "success": true,
  "message": "文件上传完成",
  "fileUrl": "/api/upload/files/2024/01/15/test.mp4",
  "fileMd5": "abc123...",
  "needContinue": false,
  "uploadedChunks": 5,
  "totalChunks": 5
}
```

### 5. 获取已上传分片（断点续传）
```http
GET /api/upload/chunks/{fileMd5}?totalChunks=10
```

响应示例：
```json
{
  "success": true,
  "uploadedChunks": [0, 1, 2, 4, 5],
  "totalChunks": 10
}
```

## 配置说明

### application.yml 配置项

```yaml
# 分片大小（字节）默认 2MB
upload:
  chunk-size: 2097152
  
# 单文件最大大小（字节）默认 10GB
  max-file-size: 10737418240
  
# 每日上传次数限制
  daily-limit: 100
  
# 上传频率限制（次/分钟）
  rate-limit: 10
  
# 本地存储路径
  storage-path: ./uploads

# Redis 配置
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: 
      database: 0
```

### 环境变量（推荐生产环境使用）

```bash
# 阿里云 OSS 配置
ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
ALIYUN_OSS_ACCESS_KEY_ID=your-access-key-id
ALIYUN_OSS_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_OSS_BUCKET_NAME=your-bucket-name

# Redis 配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0

# 文件存储路径
UPLOAD_STORAGE_PATH=/data/uploads
```

## 快速开始

### 1. 启动 Redis
```bash
docker run -d -p 6379:6379 redis:latest
```

### 2. 修改配置
编辑 `src/main/resources/application.yml` 或使用环境变量

### 3. 编译项目
```bash
mvn clean install
```

### 4. 运行服务
```bash
mvn spring-boot:run
```

### 5. 测试上传
```bash
curl http://localhost:8080/api/upload/health
```

## 前端集成流程

### 完整上传流程

1. **计算文件 MD5**
```javascript
const file = document.querySelector('input[type=file]').files[0];
const md5 = await calculateFileMD5(file); // 使用 spark-md5
```

2. **检查文件是否存在**
```javascript
const checkResponse = await fetch('/api/upload/check', {
  method: 'POST',
  body: new URLSearchParams({ fileMd5: md5 })
});
const result = await checkResponse.json();

if (result.success && !result.needContinue) {
  console.log('秒传成功:', result.fileUrl);
  return;
}
```

3. **初始化上传**
```javascript
const initResponse = await fetch('/api/upload/init', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    fileMd5: md5,
    fileName: file.name,
    fileSize: file.size,
    contentType: file.type,
    userId: 'user123'
  })
});
const initData = await initResponse.json();
```

4. **分片上传**
```javascript
const CHUNK_SIZE = 2 * 1024 * 1024; // 2MB
const totalChunks = Math.ceil(file.size / CHUNK_SIZE);

for (let i = 0; i < totalChunks; i++) {
  const start = i * CHUNK_SIZE;
  const end = Math.min(start + CHUNK_SIZE, file.size);
  const chunk = file.slice(start, end);
  
  const formData = new FormData();
  formData.append('fileMd5', md5);
  formData.append('chunkIndex', i);
  formData.append('totalChunks', totalChunks);
  formData.append('chunkSize', chunk.size);
  formData.append('userId', 'user123');
  formData.append('chunkFile', chunk);
  
  const response = await fetch('/api/upload/chunk', {
    method: 'POST',
    body: formData
  });
  
  const result = await response.json();
  
  if (!result.needContinue) {
    console.log('上传完成:', result.fileUrl);
    break;
  }
}
```

## 技术栈

- Spring Boot 3.2.0
- Java 17
- Redis（进度存储、秒传检测）
- Guava RateLimiter（限流）
- Lombok（简化代码）

## 注意事项

1. **Redis 必须部署**：用于存储上传进度和秒传检测
2. **存储空间规划**：大文件上传需要足够的磁盘空间
3. **生产环境配置**：
   - 修改跨域配置（目前允许所有域名）
   - 添加用户认证
   - 使用 HTTPS
   - 配置合适的 JVM 参数
4. **监控告警**：建议添加上传失败率、存储空间等监控指标
