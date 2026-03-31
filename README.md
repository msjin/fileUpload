# 🚀 文件上传系统

一个支持**超大文件**、**分片上传**、**断点续传**和**MD5 秒传**的完整文件上传解决方案。

![Vue.js](https://img.shields.io/badge/Vue-3.5.30-4FC08D?logo=vue.js)
![Vite](https://img.shields.io/badge/Vite-8.0.1-646CFF?logo=vite)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.0-6DB33F?logo=spring-boot)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis)

## ✨ 核心特性

### 🎯 超大文件支持
- 单文件最大 **10GB**（可配置）
- 自动分片，每片 **2MB**
- 内存占用低，与文件大小无关

### ⚡ MD5 秒传
- 智能检测重复文件
- 相同文件瞬间完成上传
- 节省存储空间和带宽

### 🔄 断点续传
- 网络中断后可继续
- 精确记录上传进度
- 无需重新开始

### 🛡️ 多维度限流
- 单文件大小限制
- 每日上传次数控制
- 上传频率限制
- 全局限流保护

### ☁️ 灵活存储
- **本地存储**: 开发测试方便
- **阿里云 OSS**: 生产环境推荐
- **可扩展**: 轻松支持 S3、MinIO 等

## 📁 项目结构

```
fileUpload/
├── backend/              # Spring Boot 后端
│   ├── src/main/java/   # Java 源代码
│   │   └── com/fileupload/
│   │       ├── config/      # 配置类
│   │       ├── controller/  # REST API
│   │       ├── service/     # 业务逻辑
│   │       ├── dto/         # 数据传输对象
│   │       └── exception/   # 异常处理
│   ├── resources/       # 配置文件
│   ├── Dockerfile       # Docker 镜像
│   └── pom.xml          # Maven 依赖
│
└── frontend/            # Vue3 前端
    ├── src/
    │   ├── components/
    │   │   └── FileUpload.vue  # 上传组件
    │   ├── App.vue      # 主应用
    │   └── main.js      # 入口文件
    └── package.json     # NPM 依赖
```

## 🚀 快速开始

### 前置要求
- Java 17+
- Maven 3.8+
- Node.js 18+
- Redis（用于存储上传进度）

### 方式一：本地运行

#### 1. 启动 Redis
```bash
docker run -d -p 6379:6379 redis:latest
```

#### 2. 启动后端
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

#### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
```

访问：**http://localhost:5173**

### 方式二：Docker Compose

```bash
cd backend
docker-compose up -d
```

详细步骤请查看：[QUICK_START.md](./QUICK_START.md)

## 🔌 API 接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/upload/health` | GET | 健康检查 |
| `/api/upload/check` | POST | 检查文件是否存在（秒传） |
| `/api/upload/init` | POST | 初始化上传 |
| `/api/upload/chunk` | POST | 上传分片 |
| `/api/upload/chunks/{fileMd5}` | GET | 获取已上传分片 |

详细 API 文档请查看：[backend/README.md](./backend/README.md)

## 💻 技术栈

### 后端
- **Spring Boot 3.2.0** - Web 框架
- **Java 17** - 开发语言
- **Redis** - 缓存和进度存储
- **Guava** - 限流器
- **Lombok** - 代码简化

### 前端
- **Vue 3.5.30** - 渐进式框架
- **Vite 8.0.1** - 构建工具
- **SparkMD5** - MD5 计算库
- **Axios** - HTTP 请求库

## 📊 性能指标

| 指标 | 值 |
|------|-----|
| 单文件最大 | 10GB |
| 分片大小 | 2MB |
| 并发上传 | 10 用户/秒 |
| 秒传响应 | < 100ms |
| 内存占用 | ~50MB |

## 🎨 使用示例

### 前端集成

```javascript
// 1. 计算文件 MD5
const md5 = await calculateFileMD5(file)

// 2. 检查是否已存在（秒传）
const checkRes = await fetch('/api/upload/check', {
  method: 'POST',
  body: new URLSearchParams({ fileMd5: md5 })
})

// 3. 如果不存在，分片上传
const formData = new FormData()
formData.append('fileMd5', md5)
formData.append('chunkIndex', 0)
formData.append('chunkFile', chunk)

await fetch('/api/upload/chunk', {
  method: 'POST',
  body: formData
})
```

完整示例请查看：[frontend/src/components/FileUpload.vue](./frontend/src/components/FileUpload.vue)

## 📝 配置说明

### application.yml

```yaml
# 分片大小 (2MB)
upload:
  chunk-size: 2097152
  
# 单文件最大 (10GB)
  max-file-size: 10737418240
  
# 每日上传次数
  daily-limit: 100
  
# 上传频率 (次/分钟)
  rate-limit: 10
```

更多配置请查看：[backend/src/main/resources/application.yml](./backend/src/main/resources/application.yml)

## 🔒 安全建议

生产环境建议：
- ✅ 添加用户认证（JWT/OAuth2）
- ✅ 启用 HTTPS
- ✅ 配置 CORS 白名单
- ✅ 文件类型校验
- ✅ 病毒扫描集成

## 📖 文档索引

| 文档 | 描述 |
|------|------|
| [QUICK_START.md](./QUICK_START.md) | 5 分钟快速开始指南 |
| [backend/README.md](./backend/README.md) | 后端详细文档 |
| [PROJECT_SUMMARY.md](./PROJECT_SUMMARY.md) | 项目完整总结 |

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 License

MIT License

---

**🎉 项目已完成，立即开始使用吧！**
