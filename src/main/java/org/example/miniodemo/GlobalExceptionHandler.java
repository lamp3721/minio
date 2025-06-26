package org.example.miniodemo;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器。
 * <p>
 * 使用 {@link ControllerAdvice} 拦截并处理在Controller层抛出的特定异常，
 * 返回统一格式的、对用户友好的错误响应。
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理文件上传时超出预设最大大小的异常。
     *
     * @param e 捕获到的 {@link MaxUploadSizeExceededException} 异常。
     * @return 封装了错误信息的统一响应体 {@link R}。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public R<String> handleMaxSizeException(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超出限制: {}", e.getMessage());
        return R.error(ResultCode.FILE_UPLOAD_FAILED, "文件大小超出限制！");
    }

    /**
     * 处理所有其他未被特定处理器捕获的全局异常。
     *
     * @param e 捕获到的 {@link Exception} 异常。
     * @return 封装了错误信息的统一响应体 {@link R}。
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public R<Object> handleGlobalException(Exception e) {
        log.error("发生了未捕获的异常: {}", e.getMessage(), e);
        return R.error(ResultCode.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}
