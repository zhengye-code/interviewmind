package com.interviewmind.modules.interview.model;

import java.time.LocalDateTime;

public record SummaryQuestionResponse(
        Long sessionId,
        Integer questionIndex,
        String questionText,
        String answerText,
        Long timeUsed
) {
}
