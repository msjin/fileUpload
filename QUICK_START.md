# 文件上传系统 - 快速启动指南

## 🎯 5 分钟快速体验

### 前置要求
- Java 17+
- Maven 3.8+
- Node.js 18+
- Redis（可选，用于完整功能）

---

## 方式一：本地运行（推荐开发使用）

### Step 1: 启动 Redis（必需）
```bash
# 使用 Docker
docker run -d -p 6379:6379 --name file-upload-redis redis:latest

# 或者本地安装 Redis
# Windows: 下载 Redis for Windows
# Linux: sudo apt-get install redis-server
# Mac: brew install redis
```

### Step 2: 配置后端
编辑 `backend/src/main/resources/application.yml`：

```yaml
# Redis 配置（默认即可）
spring:
  data:
    redis:
      host: localhost
      port: 6379

# 阿里云 OSS 配置（测试可先用本地存储）
aliyun:
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: 你的 AccessKey
    access-key-secret: 你的 Secret
    bucket-name: 你的 Bucket 名称
```

### Step 3: 启动后端服务
```bash
cd backend

# 编译项目
mvn clean install -DskipTests

# 运行应用
mvn spring-boot:run
```

看到以下日志表示启动成功：
```
Started FileUploadApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

### Step 4: 启动前端
打开新终端：
```bash
cd frontend

# 安装依赖（首次运行）
npm install

# 启动开发服务器
npm run dev
```

看到以下日志表示启动成功：
```
  VITE v8.x.x  ready in xxx ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### Step 5: 访问应用
浏览器打开：**http://localhost:5173**

---

## 方式二：Docker Compose 部署（推荐生产使用）

### Step 1: 准备环境变量
在 `backend` 目录下创建 `.env` 文件：

```bash
# 阿里云 OSS 配置
ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
ALIYUN_OSS_ACCESS_KEY_ID=your-access-key-id
ALIYUN_OSS_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_OSS_BUCKET_NAME=your-bucket-name
```

### Step 2: 一键部署
```bash
cd backend

# 构建并启动
docker-compose up -d

# 查看日志
docker-compose logs -f backend

# 停止服务
docker-compose down
```

### Step 3: 访问服务
- 前端：需要单独部署（参考下方）
- 后端 API：http://localhost:8080/api
- Redis: localhost:6379

---

## 🔍 验证安装

### 测试健康检查接口
```bash
curl http://localhost:8080/api/upload/health
```

预期响应：
```json
{
  "status": "UP",
  "message": "File Upload Service is running"
}
```

### 测试文件上传
1. 访问 http://localhost:5173
2. 选择一个文件（建议先选小文件测试）
3. 观察上传进度
4. 上传成功后会显示文件地址

---

## ⚙️ 配置说明

### 关键配置项

| 配置项 | 位置 | 默认值 | 说明 |
|--------|------|--------|------|
| 分片大小 | application.yml | 2MB | 大文件切割大小 |
| 最大文件大小 | application.yml | 10GB | 单文件上限 |
| 每日上传次数 | application.yml | 100 | 每用户每天限制 |
| 上传频率 | application.yml | 10 次/分钟 | 每用户每分钟限制 |
| 存储路径 | application.yml | ./uploads | 文件本地存储位置 |

### 修改配置示例

**增大分片大小到 5MB：**
```yaml
upload:
  chunk-size: 5242880  # 5MB
```

**修改每日上传限制到 200 次：**
```yaml
upload:
  daily-limit: 200
```

---

## 🐛 常见问题排查

### 问题 1: 后端启动失败，提示 Redis 连接错误
**解决方案：**
```bash
# 检查 Redis 是否运行
docker ps | grep redis

# 或者重启 Redis
docker restart file-upload-redis

# 检查 Redis 连通性
redis-cli ping  # 应返回 PONG
```

### 问题 2: 前端无法连接后端
**解决方案：**
1. 检查后端是否正常启动：`curl http://localhost:8080/api/upload/health`
2. 修改前端代理配置（如果需要）
3. 检查 CORS 配置：`backend/src/main/java/com/fileupload/controller/FileUploadController.java#L36`

### 问题 3: 上传大文件时内存溢出
**解决方案：**
修改 JVM 参数（`backend/Dockerfile` 或启动命令）：
```bash
JAVA_OPTS="-Xms512m -Xmx4g -XX:+UseG1GC"
```

### 问题 4: 跨域问题
**解决方案：**
后端已配置跨域，如果还有问题检查：
- 前端请求的 URL 是否正确
- 浏览器控制台的具体错误信息

---

## 📝 测试建议

### 小文件测试（< 10MB）
- 测试普通图片、文档上传
- 验证基本功能正常

### 中等文件测试（10MB - 100MB）
- 测试高清视频、压缩包
- 观察分片上传过程

### 大文件测试（> 1GB）
- 测试蓝光视频、大型压缩包
- 验证断点续传功能
- 监控内存占用

### 重复文件测试
- 上传同一文件两次
- 验证第二次是否秒传

### 断点续传测试
- 上传过程中关闭网络
- 恢复网络后继续上传
- 验证是否从断点处继续

---

## 🎨 前端自定义样式

如需修改上传组件样式，编辑：
`frontend/src/components/FileUpload.vue`

主要样式区域：
```vue
<style scoped>
.upload-container { /* 容器样式 */ }
.progress-bar { /* 进度条样式 */ }
.message { /* 消息提示样式 */ }
</style>
```

---

## 📦 打包部署

### 后端打包
```bash
cd backend
mvn clean package -DskipTests

# 生成的 jar 包位置
target/file-upload-backend-1.0.0.jar
```

### 前端打包
```bash
cd frontend
npm run build

# 生成的静态文件位置
dist/
```

### 生产环境部署
1. 将前端 `dist/` 目录部署到 Nginx 或 CDN
2. 后端 jar 包部署到服务器
3. 配置 Nginx 反向代理
4. 配置 HTTPS 证书
5. 设置防火墙规则

---

## 🆘 获取帮助

### 查看日志
```bash
# 后端日志
tail -f backend/logs/application.log

# Docker 日志
docker-compose logs -f backend
```

### 其他资源
- 详细文档：`backend/README.md`
- 项目总结：`PROJECT_SUMMARY.md`
- API 文档：运行后访问 Swagger（如配置）

---

## ✅ 检查清单

启动前确认：
- [ ] Redis 已启动且可连接
- [ ] Java 17 已安装
- [ ] Maven 3.8+ 已安装
- [ ] Node.js 18+ 已安装
- [ ] 配置文件已修改（如需要）

启动后验证：
- [ ] 后端健康检查接口正常
- [ ] 前端页面可访问
- [ ] 可以成功上传小文件
- [ ] 可以成功上传大文件
- [ ] 重复文件可以秒传

---

**祝你使用愉快！🎉**

如有问题，请查看日志文件或联系技术支持。
