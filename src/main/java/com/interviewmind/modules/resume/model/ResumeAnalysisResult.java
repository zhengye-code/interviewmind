package com.interviewmind.modules.resume.model;



import java.util.List;

/**
 * 大模型返回的分析结果结构。
 * 字段名必须和提示词里要求的 JSON 字段一一对应。
 */
public record ResumeAnalysisResult(
        int overallScore,
        ScoreDetail scoreDetail,
        String summary,
        List<String> strengths,
        List<String> suggestions) {

    public record ScoreDetail(
            int projectScore,
            int skillMatchScore,
            int contentScore,
            int structureScore,
            int expressionScore) {
    }
}