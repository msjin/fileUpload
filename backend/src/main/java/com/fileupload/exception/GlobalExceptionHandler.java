package com.fileupload.exception;

import com.fileupload.dto.UploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 文件大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<UploadResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        UploadResponse response = UploadResponse.builder()
                .success(false)
                .message("上传文件大小超过系统限制")
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    /**
     * 文件上传异常
     */
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<UploadResponse> handleFileUploadException(FileUploadException e) {
        UploadResponse response = UploadResponse.builder()
                .success(false)
                .message(e.getMessage())
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 限流异常
     */
    @ExceptionHandler(FileUploadException.RateLimitExceededException.class)
    public ResponseEntity<UploadResponse> handleRateLimitExceededException(
            FileUploadException.RateLimitExceededException e) {
        UploadResponse response = UploadResponse.builder()
                .success(false)
                .message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    /**
     * 通用异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<UploadResponse> handleException(Exception e) {
        UploadResponse response = UploadResponse.builder()
                .success(false)
                .message("服务器内部错误：" + e.getMessage())
                .build();
        return ResponseEntity.internalServerError().body(response);
    }
}
