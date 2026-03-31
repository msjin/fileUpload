package com.fileupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 文件 URL
     */
    private String fileUrl;

    /**
     * 文件 MD5
     */
    private String fileMd5;

    /**
     * 是否需要继续上传分片
     */
    private Boolean needContinue;

    /**
     * 已上传的分片数量
     */
    private Integer uploadedChunks;

    /**
     * 总分片数
     */
    private Integer totalChunks;
}
