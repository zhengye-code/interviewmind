package com.interviewmind.modules.interview.model.response;

import com.interviewmind.modules.interview.model.backendModel.QuestionScore;

import java.time.LocalDateTime;
import java.util.List;

/** 评分结果（返回给前端） */
public record InterviewEvaluationResponse(
        Long sessionId,
        int overallScore,
        int scoredQuestions,
        String summary,
        List<String> strengths,
        List<String> suggestions,
        String model,
        LocalDateTime evaluatedAt,
        List<QuestionScore> questionScores) {


}