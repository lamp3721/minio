package org.example.miniodemo.common.response;

import lombok.Getter;

/**
 * API 统一返回状态码枚举。
 * <p>
 * 包含了通用的HTTP状态码以及自定义的业务相关状态码。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    /* 客户端错误 */
    BAD_REQUEST(400, "无效的请求"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),

    /* 服务端错误 */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    /* 业务错误 */
    FILE_UPLOAD_FAILED(1001, "文件上传失败"),
    FILE_DOWNLOAD_FAILED(1002, "文件下载失败"),
    FILE_DELETE_FAILED(1003, "文件删除失败"),
    FILE_EXISTS(1004, "文件已存在"),
    BUCKET_CREATION_FAILED(1005, "存储桶创建失败"),
    MERGE_FAILED(1006, "文件合并失败"),
    FILE_NOT_EXIST(1007, "文件不存在");


    /**
     * 状态码
     */
    private final int code;
    /**
     * 状态码描述
     */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
} 