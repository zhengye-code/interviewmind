package com.interviewmind.modules.interview.model;

import java.util.List;

/**
 * AI 返回的评分结果。
 *
 * ⚠️ 字段顺序是有意义的：模型按 Schema 的属性顺序生成，
 *    所以"证据在前、结论在后"能显著减少模型乱给高分。
 */
public record InterviewEvaluationResult(
        List<QuestionScore> questionScores,
        String summary,
        List<String> strengths,
        List<String> suggestions) {
}