package com.interviewmind.modules.interview.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank(message = "skillId 不能为空")
        String skillId,

        /** 可选：绑定一份简历，PROJECT 类题目会结合简历提问 */
        Long resumeId,

        @Min(value = 1, message = "题目数量至少 1 道")
        @Max(value = 20, message = "题目数量最多 20 道")
        Integer totalQuestions,

        /** 可选：EASY / MEDIUM / HARD，不传默认 MEDIUM */
        String difficulty) {
}