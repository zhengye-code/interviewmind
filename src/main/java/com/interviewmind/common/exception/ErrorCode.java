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
    LOADING_ERROR(1000,"加载失败"),
    //AI错误模块
    AI_SERVICE_UNAVAILABLE(7001, "AI服务暂时不可用，请稍后重试"),
    AI_API_KEY_INVALID(7004, "AI服务密钥无效"),
    AI_SERVICE_ERROR(7003, "AI服务调用失败"),
    // 简历模块
    RESUME_NOT_FOUND(2001, "简历不存在"),
    RESUME_FILE_EMPTY(2002, "上传文件为空"),
    RESUME_FILE_TYPE_NOT_SUPPORTED(2003, "不支持的文件类型"),
    RESUME_FILE_TOO_LARGE(2004, "文件大小超过限制"),
    RESUME_UPLOAD_FAILED(2005, "简历上传失败"),
    RESUME_PARSE_FAILED(2006, "简历解析失败"),
    RESUME_ANALYSIS_FAILED(2007, "简历分析失败"),
    // 面试模块
    INTERVIEW_SKILL_NOT_FOUND(3001,"面试方向不存在"),
    INTERVIEW_SESSION_NOT_FOUND(3002,"面试会话不存在"),
    INTERVIEW_QUESTION_NOT_FOUND(3003,"面试题目不存在"),
    INTERVIEW_SESSION_STATE_INVALID(3004, "面试会话当前状态不允许该操作"),
    INTERVIEW_QUESTION_GENERATION_FAILED(3005, "面试题目生成失败"),
    INTERVIEW_GRADING_GENERATION_FAILED(3006,"AI打分失败"),
    INTERVIEW_QUESTION_SEQUENCE_NOT_MATCH(3007,"答题顺序不匹配"),
    INTERVIEW_REPORT_NOT_FOUND(3008,"该面试还没有评分报告"),
    //RAG模块
    RAG_DOCUMENT_NOT_FOUND(4001,"未找到文档");
    private final Integer code;
    private final String message;

}
