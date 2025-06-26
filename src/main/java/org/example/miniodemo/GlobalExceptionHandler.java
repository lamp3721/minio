package org.example.miniodemo;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.example.miniodemo.common.response.R;
import org.example.miniodemo.common.response.ResultCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public R<String> handleMaxSizeException(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超出限制: {}", e.getMessage());
        return R.error(ResultCode.FILE_UPLOAD_FAILED, "文件大小超出限制！");
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public R<Object> handleGlobalException(Exception e) {
        log.error("发生了未捕获的异常: {}", e.getMessage(), e);
        return R.error(ResultCode.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}
