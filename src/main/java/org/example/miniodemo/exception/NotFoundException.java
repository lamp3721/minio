package org.example.miniodemo.exception;

import org.example.miniodemo.common.response.ResultCode;

/**
 * 资源未找到异常
 */
public class NotFoundException extends BusinessException {

    public NotFoundException() {
        super(ResultCode.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(ResultCode.NOT_FOUND, message);
    }
}