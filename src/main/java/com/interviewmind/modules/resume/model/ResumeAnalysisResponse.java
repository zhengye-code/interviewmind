package com.interviewmind.modules.resume.model;

import java.time.LocalDateTime;
import java.util.List;

/** 返回给前端的分析结果（扁平结构，便于前端直接用） */
public record ResumeAnalysisResponse(
        Long resumeId,
        Long analysisId,
        int overallScore,
        int projectScore,
        int skillMatchScore,
        int contentScore,
        int structureScore,
        int expressionScore,
        String summary,
        List<String> strengths,
        List<String> suggestions,
        LocalDateTime analyzedAt) {
}