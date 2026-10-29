package com.interviewmind.modules.resume.model;

/** 简历分析进度，供前端轮询 */
public record ResumeStatusResponse(
        Long resumeId,
        String analyzeStatus,
        String analyzeError,
        Integer overallScore) {
}