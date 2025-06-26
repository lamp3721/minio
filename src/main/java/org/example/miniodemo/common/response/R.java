package org.example.miniodemo.common.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一API响应结果封装
 *
 * @param <T>
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> success(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> success() {
        return success(null);
    }

    public static <T> R<T> error(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> error(ResultCode resultCode, T data) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), data);
    }

    public static <T> R<T> error(int code, String message) {
        return new R<>(code, message, null);
    }
} 