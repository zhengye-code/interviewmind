package com.interviewmind.modules.interview.model.response;

public record SummaryQuestionResponse(
        Long sessionId,
        Integer questionIndex,
        String questionText,
        String answerText,
        Long timeUsed
) {
}
