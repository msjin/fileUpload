package com.fileupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequest {

    /**
     * 文件 MD5 摘要
     */
    private String fileMd5;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小 (字节)
     */
    private Long fileSize;

    /**
     * 文件类型 (MIME)
     */
    private String contentType;

    /**
     * 用户 ID
     */
    private String userId;
}
