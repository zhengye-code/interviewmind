package com.interviewmind.modules.interview.model;

import java.time.LocalDateTime;
import java.util.List;

public record SessionResponse(
        Long sessionId,
        String skillId,
        String skillName,
        Long resumeId,
        String status,
        String difficulty,
        int totalQuestions,
        int currentQuestionIndex,
        List<CategoryQuota> plan,
        LocalDateTime createdAt) {
}