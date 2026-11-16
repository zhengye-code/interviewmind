package com.interviewmind.modules.interview.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerRequest(@NotBlank(message = "回答内容不能为空")
                            @Size(max = 5000, message = "回答过长，最多 5000 字")
                            String answerText) {
}
