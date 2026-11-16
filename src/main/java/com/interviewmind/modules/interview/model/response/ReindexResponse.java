package com.interviewmind.modules.interview.model.response;

public record ReindexResponse(int imported, int skipped, int failed, int totalChunks) {
}