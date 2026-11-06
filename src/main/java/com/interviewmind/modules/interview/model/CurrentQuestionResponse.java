package com.interviewmind.modules.interview.model;

public record CurrentQuestionResponse(Long sessionId,
                                      String status,
                                      int currentIndex,
                                      int totalQuestions,
                                      QuestionResponse question) {
}
