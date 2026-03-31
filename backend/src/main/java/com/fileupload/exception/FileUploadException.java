package com.fileupload.exception;

/**
 * 文件上传异常
 */
public class FileUploadException extends RuntimeException {

    public FileUploadException(String message) {
        super(message);
    }

    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 文件已存在 (秒传)
     */
    public static class FileExistsException extends FileUploadException {
        public FileExistsException(String message) {
            super(message);
        }
    }

    /**
     * 文件大小超限
     */
    public static class FileSizeExceededException extends FileUploadException {
        public FileSizeExceededException(String message) {
            super(message);
        }
    }

    /**
     * 分片上传失败
     */
    public static class ChunkUploadFailed extends FileUploadException {
        public ChunkUploadFailed(String message) {
            super(message);
        }
    }

    /**
     * 限流异常
     */
    public static class RateLimitExceededException extends FileUploadException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    /**
     * MD5 不匹配
     */
    public static class MD5MismatchException extends FileUploadException {
        public MD5MismatchException(String message) {
            super(message);
        }
    }
}
