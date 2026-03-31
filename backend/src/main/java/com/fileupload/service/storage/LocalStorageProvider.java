package com.fileupload.service.storage;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 本地文件存储实现
 */
@Component("local")
public class LocalStorageProvider extends AbstractStorageProvider {

    @Override
    public String getType() {
        return "local";
    }

    @Override
    protected void doSaveChunk(String key, int chunkIndex, InputStream inputStream) throws Exception {
        Path chunkPath = getChunkPath(key, chunkIndex);
        Files.createDirectories(chunkPath.getParent());
        
        try (java.io.OutputStream outputStream = Files.newOutputStream(chunkPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    @Override
    protected void doMergeChunks(String key, String fileName, int totalChunks) throws Exception {
        Path chunkDir = getChunkDir(key);
        List<Path> chunks = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(chunkDir)) {
            stream.sorted((p1, p2) -> {
                try {
                    return Integer.compare(
                        Integer.parseInt(p1.getFileName().toString()),
                        Integer.parseInt(p2.getFileName().toString())
                    );
                } catch (NumberFormatException e) {
                    return 0;
                }
            }).forEach(chunks::add);
        }

        LocalDate today = LocalDate.now();
        String dateDir = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path finalDir = Paths.get(storagePath, "files", dateDir);
        Files.createDirectories(finalDir);

        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        Path finalFilePath = finalDir.resolve(uniqueFileName);

        try (java.io.OutputStream outputStream = Files.newOutputStream(finalFilePath)) {
            for (Path chunk : chunks) {
                Files.copy(chunk, outputStream);
            }
        }

        // 存储文件路径到 Redis，用于后续查询
        String fileKey = "file:" + key;
        String relativePath = "/files/" + dateDir + "/" + uniqueFileName;
        redisTemplate.opsForValue().set(fileKey, relativePath);
    }

    @Override
    protected String doGetFileUrl(String key) {
        String fileKey = "file:" + key;
        Object path = redisTemplate.opsForValue().get(fileKey);
        if (path != null) {
            return path.toString();
        }
        return null;
    }

    @Override
    protected boolean doExists(String key) {
        String fileKey = "file:" + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(fileKey));
    }

    @Override
    protected void doDelete(String key) {
        String fileKey = "file:" + key;
        Object path = redisTemplate.opsForValue().get(fileKey);
        if (path != null) {
            try {
                Path filePath = Paths.get(storagePath, path.toString());
                Files.deleteIfExists(filePath);
            } catch (Exception e) {
                log.error("删除文件失败：{}", key, e);
            }
            redisTemplate.delete(fileKey);
        }
    }

    @Override
    protected void doDeleteChunk(String key, int chunkIndex) {
        try {
            Path chunkPath = getChunkPath(key, chunkIndex);
            Files.deleteIfExists(chunkPath);
        } catch (Exception e) {
            log.error("删除分片失败：{}-{}", key, chunkIndex, e);
        }
    }

    /**
     * 获取分片存储路径
     */
    private Path getChunkPath(String key, int chunkIndex) {
        return getChunkDir(key).resolve(String.valueOf(chunkIndex));
    }

    /**
     * 获取分片目录
     */
    private Path getChunkDir(String key) {
        return Paths.get(storagePath, "chunks", key);
    }
}
