package com.interviewmind.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用错误模块
    BAD_REQUEST(400,"请求参数错误"),
    UNAUTHORIZED(401,"未授权"),
    FORBIDDEN(403,"禁止访问"),
    NOT_FOUND(404,"未找到"),
    METHOD_NOT_ALLOWED(405,"请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(415,"不支持的请求格式"),
    INTERNAL_ERROR(500,"服务器内部错误"),
    //AI错误模块
    AI_SERVICE_UNAVAILABLE(7001, "AI服务暂时不可用，请稍后重试"),
    AI_API_KEY_INVALID(7004, "AI服务密钥无效"),
    AI_SERVICE_ERROR(7003, "AI服务调用失败"),
    // 简历模块
    RESUME_NOT_FOUND(2001, "简历不存在"),
    RESUME_FILE_EMPTY(2002, "上传文件为空"),
    RESUME_FILE_TYPE_NOT_SUPPORTED(2003, "不支持的文件类型"),
    RESUME_FILE_TOO_LARGE(2004, "文件大小超过限制"),
    RESUME_UPLOAD_FAILED(2005, "简历上传失败");
    // 面试模块
    private final Integer code;
    private final String message;

}
