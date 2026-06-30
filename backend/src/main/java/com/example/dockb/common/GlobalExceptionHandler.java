package com.example.dockb.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器：将异常统一翻译为 {@link Result}，HTTP 状态码使用 200/400/500。
 *
 * <p>所有接口返回值结构都遵循 {@link Result}，避免前端处理非 200 的尴尬。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBiz(BizException e) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (e.getCode() == 404 || e.getCode() == ResultCode.NOT_FOUND.getCode()
                || e.getCode() == ResultCode.DOCUMENT_NOT_FOUND.getCode()) {
            status = HttpStatus.NOT_FOUND;
        } else if (e.getCode() >= 400 && e.getCode() < 500) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Result<Object>> handleBadRequest(Exception e) {
        log.warn("BadRequest: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Object>> handleUpload(MaxUploadSizeExceededException e) {
        log.warn("Upload too large: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), "上传文件超过 20MB 限制"));
    }

    @ExceptionHandler(com.example.dockb.client.M3Exception.class)
    public ResponseEntity<Result<Object>> handleM3(com.example.dockb.client.M3Exception e) {
        log.error("M3Exception: {}", e.getMessage());
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("401")
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(Result.fail(ResultCode.INTERNAL_ERROR.getCode(), "AI 服务调用失败: " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleAny(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR.getCode(),
                        ResultCode.INTERNAL_ERROR.getMessage()));
    }
}