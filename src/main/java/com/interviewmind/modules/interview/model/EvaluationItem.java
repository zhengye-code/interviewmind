package com.interviewmind.modules.interview.model;

import java.util.List;

/**
 * 一道题的评分材料。
 *
 * 为什么不用 InterviewQuestionEntity 直接传？
 *   因为"AI 层"不该认识数据库实体。给它一个只含 5 个字段的小对象，
 *   以后实体加列（比如加 score_version）时，AI 层完全不受影响。
 */
public record EvaluationItem(
        int questionIndex,
        String categoryKey,
        String questionText,
        List<String> expectedPoints,
        String answerText) {
}