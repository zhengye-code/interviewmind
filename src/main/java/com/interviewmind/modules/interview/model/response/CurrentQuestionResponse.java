package com.interviewmind.modules.interview.model.response;

public record CurrentQuestionResponse(Long sessionId,
                                      String status,
                                      int currentIndex,
                                      int totalQuestions,
                                      QuestionResponse question) {
}
