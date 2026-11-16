package com.interviewmind.modules.interview.model.response;

public record QuestionResponse(
        Long questionId,
        int questionIndex,
        String categoryKey,
        String categoryLabel,
        String difficulty,
        String questionText
) {
}
