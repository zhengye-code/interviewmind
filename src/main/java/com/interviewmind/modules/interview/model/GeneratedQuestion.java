package com.interviewmind.modules.interview.model;

import java.util.List;

public record GeneratedQuestion(
        String question,
        String categoryKey,
        List<String> expectedPoints
) {
}
