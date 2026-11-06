package com.interviewmind.modules.interview.model;

public record QuestionResponse(
        Long questionId,
        int questionIndex,
        String categoryKey,
        String categoryLabel,
        String difficulty,
        String questionText
) {
}
