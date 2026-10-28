package com.interviewmind.modules.resume.model;

public record ResumeUploadResponse(
        Long id,
        String filename,
        long fileSize,
        String analyzeStatus,
        boolean duplicate
) {
}
