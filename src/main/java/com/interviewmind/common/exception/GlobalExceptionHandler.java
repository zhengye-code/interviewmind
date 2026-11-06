package com.interviewmind.common.exception;

import com.interviewmind.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e){
        log.warn("业务异常：code={},message={}",e.getCode(),e.getMessage());
        return Result.error(e.getCode(),e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.BAD_REQUEST, message);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST, e.getMessage());
    }

    /** URL 不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源未找到: {}", e.getResourcePath());
        return Result.error(ErrorCode.NOT_FOUND, "API 接口不存在");
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return Result.error(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不支持: " + e.getMethod());
    }
    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(ErrorCode.BAD_REQUEST, "缺少必填参数: " + e.getParameterName());
    }
    /** 请求的 Content-Type 不被接口接受（例如 multipart 接口传了 application/json 或干脆没带类型） */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的请求格式: {}", e.getContentType());
        return Result.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "请求格式不支持，请使用 multipart/form-data 上传文件");
    }
    /** 请求体无法解析：JSON 语法错误、字段类型不匹配、枚举值不在允许范围 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析: {}", e.getMostSpecificCause().getMessage());
        return Result.error(ErrorCode.BAD_REQUEST,
                "请求体格式错误，请检查 JSON 结构、字段类型与取值范围");
    }
    /** 兜底：所有未匹配的异常，最后一道防线 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后重试");
    }

    /** 路径/查询参数类型不匹配，例如 /sessions/abc 中的 id 不是数字 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={}, value={}", e.getName(), e.getValue());
        return Result.error(ErrorCode.BAD_REQUEST,
                "参数 " + e.getName() + " 类型不正确: " + e.getValue());
    }
}
