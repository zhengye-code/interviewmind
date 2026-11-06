package com.interviewmind.modules.interview.model;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;

public enum InterviewDifficulty {
    EASY, MEDIUM, HARD;
    public static InterviewDifficulty fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String normalized = raw.trim().toUpperCase();
        for (InterviewDifficulty difficulty : values()) {
            if (difficulty.name().equals(normalized)) {
                return difficulty;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "难度只支持 EASY / MEDIUM / HARD，收到: " + raw);
    }
}