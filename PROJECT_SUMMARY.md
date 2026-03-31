# 文件上传系统 - 完整实现总结

## 📋 项目架构

```
fileUpload/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/fileupload/
│   │   ├── config/                   # 配置类
│   │   │   ├── UploadProperties.java    # 上传配置属性
│   │   │   ├── RateLimiterConfig.java   # 限流器配置
│   │   │   └── RedisConfig.java         # Redis 配置
│   │   ├── controller/               # REST 控制器
│   │   │   └── FileUploadController.java
│   │   ├── service/                  # 业务逻辑层
│   │   │   └── FileUploadService.java
│   │   ├── dto/                      # 数据传输对象
│   │   │   ├── FileUploadRequest.java
│   │   │   ├── ChunkUploadRequest.java
│   │   │   └── UploadResponse.java
│   │   ├── exception/                # 异常处理
│   │   │   ├── FileUploadException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── FileUploadApplication.java  # 启动类
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置文件
│   │   └── application.properties    # Properties 格式配置
│   ├── pom.xml                       # Maven 依赖管理
│   ├── Dockerfile                    # Docker 镜像构建
│   ├── docker-compose.yml            # Docker 编排
│   └── README.md                     # 后端文档
│
└── frontend/                         # Vue3 前端
    ├── src/
    │   ├── components/
    │   │   └── FileUpload.vue        # 上传组件
    │   ├── App.vue                   # 主应用
    │   └── main.js                   # 入口文件
    ├── package.json                  # NPM 依赖
    └── vite.config.js                # Vite 配置
```

## 🎯 核心功能实现

### 1. 超大文件支持（分片上传）

**原理：**
- 前端使用 `File.slice()` 将文件切割成 2MB 的分片
- 每个分片独立上传，后端接收后立即落盘
- 内存占用仅取决于分片大小，与文件总大小无关

**代码位置：**
- 前端：`frontend/src/components/FileUpload.vue#L97-L116`
- 后端：`backend/src/main/java/com/fileupload/service/FileUploadService.java#L108-L154`

**流程：**
```
10GB 文件 → 切成 5000 个 2MB 分片 → 逐片上传 → 后端合并
```

### 2. MD5 秒传（去重机制）

**原理：**
- 使用 SparkMD5 计算文件哈希摘要
- 上传前检查 Redis 中是否存在该 MD5
- 存在则直接返回已存储的文件 URL

**Redis 数据结构：**
```
Key: file:md5:{md5_value}
Value: 文件访问路径
TTL: 30 天
```

**代码位置：**
- 前端 MD5 计算：`frontend/src/components/FileUpload.vue#L14-L39`
- 后端秒传检测：`backend/src/main/java/com/fileupload/service/FileUploadService.java#L46-L62`

**效果：**
- 相同文件第二次上传瞬间完成
- 节省存储空间和网络带宽

### 3. 断点续传

**原理：**
- Redis 记录已上传的分片索引
- 网络中断后查询已上传的分片
- 跳过已上传的分片，继续上传剩余部分

**Redis Key 设计：**
```
chunk:uploaded:{fileMd5} - 记录已上传的分片数
upload:{uploadId} - 上传会话元数据
```

**代码位置：**
- 进度查询：`backend/src/main/java/com/fileupload/service/FileUploadService.java#L156-L165`
- 前端重试：`frontend/src/components/FileUpload.vue#L75-L84`

### 4. 多维度限流

**限流策略：**
```yaml
# 单文件大小限制
max-file-size: 10GB

# 每日上传次数
daily-limit: 100 次/天

# 上传频率
rate-limit: 10 次/分钟

# 全局限流 (Guava RateLimiter)
并发控制
```

**代码位置：**
- 限流器配置：`backend/src/main/java/com/fileupload/config/RateLimiterConfig.java`
- 限流检查：`backend/src/main/java/com/fileupload/controller/FileUploadController.java#L82-L85`

**Redis 计数器：**
```
upload:daily:{date}:{userId} - 每日上传次数统计
```

## 🔌 API 接口一览

| 接口 | 方法 | 描述 | 参数 |
|------|------|------|------|
| `/api/upload/health` | GET | 健康检查 | - |
| `/api/upload/check` | POST | 检查文件是否存在 | fileMd5 |
| `/api/upload/init` | POST | 初始化上传 | FileUploadRequest |
| `/api/upload/chunk` | POST | 上传分片 | MultipartFile + 分片参数 |
| `/api/upload/chunks/{fileMd5}` | GET | 获取已上传分片 | totalChunks |

## 🚀 快速开始

### 方式一：本地运行

#### 1. 启动 Redis
```bash
docker run -d -p 6379:6379 redis:latest
```

#### 2. 配置后端
编辑 `backend/src/main/resources/application.yml`：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

aliyun:
  oss:
    access-key-id: your-key
    access-key-secret: your-secret
    bucket-name: your-bucket
```

#### 3. 启动后端
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

#### 4. 启动前端
```bash
cd frontend
npm install
npm run dev
```

### 方式二：Docker Compose 部署

```bash
cd backend

# 设置环境变量
export ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
export ALIYUN_OSS_ACCESS_KEY_ID=your-key
export ALIYUN_OSS_ACCESS_KEY_SECRET=your-secret
export ALIYUN_OSS_BUCKET_NAME=your-bucket

# 一键部署
docker-compose up -d
```

访问地址：
- 前端：http://localhost:5173
- 后端 API：http://localhost:8080/api

## 📊 技术栈对比

### 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | Web 框架 |
| Java | 17 | 开发语言 |
| Redis | - | 缓存、进度存储 |
| Guava | 33.0.0-jre | 限流器 |
| Lombok | - | 简化代码 |

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.30 | 前端框架 |
| Vite | 8.0.1 | 构建工具 |
| SparkMD5 | 3.0.2 | MD5 计算 |
| Axios | 1.14.0 | HTTP 请求 |

## 💡 关键设计决策

### 1. 为什么选择 MD5 而不是 SHA？
- MD5 计算速度更快
- 对于文件去重场景，碰撞概率可接受
- 前端库成熟（SparkMD5）

### 2. 为什么分片大小设为 2MB？
- 太小：HTTP 请求过多，增加服务器压力
- 太大：内存占用高，重试成本大
- 2MB 是经验和性能的平衡点

### 3. 为什么使用 Redis 而不是数据库？
- 上传进度是临时数据，不需要持久化
- Redis 读写性能更好
- 支持原子操作（INCR）

### 4. 限流为什么用 Guava RateLimiter？
- 单机限流足够高效
- 令牌桶算法平滑限流
- 无需额外依赖 Redis

## ⚠️ 生产环境注意事项

### 1. 安全性
- ✅ 添加用户认证（JWT/OAuth2）
- ✅ 启用 HTTPS
- ✅ 修改跨域配置（限制具体域名）
- ✅ 文件类型白名单校验
- ✅ 病毒扫描集成

### 2. 性能优化
- ✅ CDN 加速文件访问
- ✅ 对接对象存储（阿里云 OSS/S3）
- ✅ 分片上传支持并发（同时上传多个分片）
- ✅ 异步合并分片（避免阻塞请求）

### 3. 监控告警
- ✅ 上传失败率监控
- ✅ 存储空间使用率监控
- ✅ 接口响应时间监控
- ✅ Redis 连接池监控

### 4. 容灾备份
- ✅ 定期备份 Redis 数据
- ✅ 对象存储开启版本控制
- ✅ 多可用区部署

## 📈 性能指标

### 理论性能（参考）
- **单文件最大**: 10GB
- **并发上传**: 10 用户/秒
- **分片大小**: 2MB
- **上传速度**: 取决于网络带宽
- **秒传响应**: < 100ms
- **内存占用**: ~50MB（每分片 2MB）

### 实际测试建议
```bash
# 生成测试文件（Linux/Mac）
dd if=/dev/zero of=test_1gb.bin bs=1M count=1024

# 使用 curl 测试上传
curl -X POST http://localhost:8080/api/upload/chunk \
  -F "fileMd5=xxx" \
  -F "chunkIndex=0" \
  -F "totalChunks=512" \
  -F "chunkSize=2097152" \
  -F "userId=test" \
  -F "chunkFile=@test_chunk_0.bin"
```

## 🔮 扩展方向

### 短期优化
1. 支持分片并发上传（提升上传速度）
2. 添加文件预览功能
3. 实现文件夹批量上传
4. 上传任务队列管理

### 长期规划
1. 分布式部署（Redis Cluster）
2. 微服务拆分
3. 视频转码集成
4. AI 内容审核
5. 下载限速和防盗链

## 📝 常见问题 FAQ

### Q1: 上传失败如何处理？
A: 前端会自动从上次中断的分片继续上传，无需重新开始。

### Q2: 如何清理未完成的上传？
A: Redis 中的数据 7 天后自动过期，临时文件会随之下线删除。

### Q3: 能否支持超过 10GB 的文件？
A: 可以，修改 `application.yml` 中的 `max-file-size` 配置即可。

### Q4: 是否必须使用阿里云 OSS？
A: 当前实现支持本地存储，对接 OSS 只需实现对应的 Storage 接口。

### Q5: 如何应对 DDoS 攻击？
A: 在生产环境添加 Nginx 限流、WAF 防火墙、IP 黑名单等多层防护。

## 📚 参考资料

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Vue 3 官方文档](https://vuejs.org/)
- [Redis 官方文档](https://redis.io/docs/)
- [阿里云 OSS SDK](https://help.aliyun.com/product/31815.html)

---

**项目已完成！🎉**

你现在拥有：
✅ 完整的分片上传功能
✅ MD5 秒传机制
✅ 断点续传支持
✅ 多维度限流保护
✅ Docker 一键部署
✅ 前后端分离架构

立即运行体验吧！🚀
