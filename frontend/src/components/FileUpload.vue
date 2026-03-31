<script setup>
import { ref } from 'vue'
import SparkMD5 from 'spark-md5'

const fileInput = ref(null)
const uploadProgress = ref(0)
const uploading = ref(false)
const message = ref('')

// 分片大小 2MB
const CHUNK_SIZE = 2 * 1024 * 1024

/**
 * 计算文件 MD5（流式计算，避免内存溢出）
 */
const calculateFileMD5 = (file) => {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    const chunkSize = 2097152 // 2MB chunks for MD5 calculation
    let currentChunk = 0
    const chunks = Math.ceil(file.size / chunkSize)

    const loadNext = () => {
      const start = currentChunk * chunkSize
      const end = Math.min(start + chunkSize, file.size)
      
      reader.readAsArrayBuffer(file.slice(start, end))
    }

    reader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk++
      
      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }

    reader.onerror = () => reject(new Error('文件读取失败'))
    loadNext()
  })
}

/**
 * 选择文件并上传
 */
const handleFileChange = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  uploading.value = true
  message.value = ''
  uploadProgress.value = 0

  try {
    // 1. 计算文件 MD5
    message.value = '正在计算文件 MD5...'
    const fileMd5 = await calculateFileMD5(file)
    console.log('文件 MD5:', fileMd5)

    // 2. 检查文件是否存在（秒传）
    message.value = '正在检查文件...'
    const checkResponse = await fetch('http://localhost:8080/api/upload/check', {
      method: 'POST',
      body: new URLSearchParams({ fileMd5 })
    })
    const checkResult = await checkResponse.json()

    if (checkResult.success && !checkResult.needContinue) {
      // 秒传成功
      message.value = `✓ 秒传成功！文件地址：${checkResult.fileUrl}`
      uploading.value = false
      uploadProgress.value = 100
      return
    }

    // 3. 初始化上传
    message.value = '正在初始化上传...'
    const initResponse = await fetch('http://localhost:8080/api/upload/init', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fileMd5,
        fileName: file.name,
        fileSize: file.size,
        contentType: file.type || 'application/octet-stream',
        userId: 'user-' + Date.now() // 实际场景应该从登录信息获取
      })
    })
    const initResult = await initResponse.json()

    if (!initResult.success) {
      throw new Error(initResult.message)
    }

    // 4. 检查已上传的分片（断点续传）
    let uploadedChunks = []
    if (initResult.uploadedChunks > 0) {
      message.value = '检查上传进度...'
      const chunksResponse = await fetch(`http://localhost:8080/api/upload/chunks/${fileMd5}?totalChunks=${initResult.totalChunks}`)
      const chunksResult = await chunksResponse.json()
      if (chunksResult.success) {
        uploadedChunks = chunksResult.uploadedChunks || []
      }
    }

    // 5. 分片上传
    const totalChunks = initResult.totalChunks
    let uploadedCount = uploadedChunks.length

    message.value = `开始上传，共 ${totalChunks} 个分片`

    for (let i = 0; i < totalChunks; i++) {
      // 跳过已上传的分片
      if (uploadedChunks.includes(i)) {
        console.log(`分片 ${i} 已上传，跳过`)
        continue
      }

      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)

      const formData = new FormData()
      formData.append('fileMd5', fileMd5)
      formData.append('chunkIndex', i)
      formData.append('totalChunks', totalChunks)
      formData.append('chunkSize', chunk.size)
      formData.append('userId', 'user-' + Date.now())
      formData.append('chunkFile', chunk)

      const response = await fetch('http://localhost:8080/api/upload/chunk', {
        method: 'POST',
        body: formData
      })

      const result = await response.json()

      if (!result.success) {
        throw new Error(result.message)
      }

      uploadedCount++
      uploadProgress.value = Math.round((uploadedCount / totalChunks) * 100)

      if (!result.needContinue) {
        // 上传完成
        message.value = `✓ 上传成功！文件地址：${result.fileUrl}`
        uploadProgress.value = 100
        break
      }

      // 每上传 10 个分片打印一次日志
      if (uploadedCount % 10 === 0) {
        console.log(`已上传 ${uploadedCount}/${totalChunks} 个分片`)
      }
    }

  } catch (error) {
    console.error('上传失败:', error)
    message.value = `✗ 上传失败：${error.message}`
  } finally {
    uploading.value = false
    // 清空文件选择
    if (fileInput.value) {
      fileInput.value.value = ''
    }
  }
}
</script>

<template>
  <div class="upload-container">
    <h1>🚀 大文件上传</h1>
    
    <div class="upload-area">
      <input 
        ref="fileInput"
        type="file" 
        @change="handleFileChange"
        :disabled="uploading"
        accept="*/*"
      />
    </div>

    <div v-if="uploading" class="progress-container">
      <div class="progress-bar">
        <div 
          class="progress-fill" 
          :style="{ width: uploadProgress + '%' }"
        ></div>
      </div>
      <p class="progress-text">{{ uploadProgress }}%</p>
    </div>

    <p v-if="message" class="message">{{ message }}</p>

    <div class="info-box">
      <h3>💡 功能说明</h3>
      <ul>
        <li>✓ 支持超大文件上传（最大 10GB）</li>
        <li>✓ 分片上传，每片 2MB</li>
        <li>✓ MD5 秒传，重复文件不重复存储</li>
        <li>✓ 断点续传，网络中断可继续</li>
        <li>✓ 限流保护，防止服务器过载</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 50px auto;
  padding: 30px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
}

h1 {
  text-align: center;
  color: #333;
  margin-bottom: 40px;
}

.upload-area {
  border: 3px dashed #4a90e2;
  border-radius: 10px;
  padding: 40px;
  text-align: center;
  background-color: #f8f9fa;
  transition: all 0.3s ease;
}

.upload-area:hover {
  border-color: #357abd;
  background-color: #eef4fc;
}

input[type="file"] {
  display: none;
}

.upload-area label {
  cursor: pointer;
  color: #4a90e2;
  font-size: 16px;
  font-weight: 500;
}

.progress-container {
  margin-top: 30px;
}

.progress-bar {
  width: 100%;
  height: 20px;
  background-color: #e0e0e0;
  border-radius: 10px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4a90e2, #357abd);
  transition: width 0.3s ease;
}

.progress-text {
  text-align: center;
  margin-top: 10px;
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.message {
  margin-top: 20px;
  padding: 15px;
  border-radius: 5px;
  background-color: #e8f5e9;
  color: #2e7d32;
  text-align: center;
  font-size: 16px;
}

.info-box {
  margin-top: 40px;
  padding: 20px;
  background-color: #f5f5f5;
  border-radius: 8px;
  border-left: 4px solid #4a90e2;
}

.info-box h3 {
  margin-bottom: 15px;
  color: #333;
}

.info-box ul {
  list-style: none;
  padding: 0;
}

.info-box li {
  padding: 8px 0;
  color: #555;
  font-size: 15px;
}
</style>
