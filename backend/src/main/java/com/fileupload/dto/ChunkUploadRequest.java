package com.fileupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadRequest {

    /**
     * 文件 MD5 摘要
     */
    private String fileMd5;

    /**
     * 分片序号 (从 0 开始)
     */
    private Integer chunkIndex;

    /**
     * 分片总数
     */
    private Integer totalChunks;

    /**
     * 分片大小 (字节)
     */
    private Long chunkSize;

    /**
     * 用户 ID
     */
    private String userId;
}
