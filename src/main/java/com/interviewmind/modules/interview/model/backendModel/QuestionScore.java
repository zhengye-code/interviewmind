package com.interviewmind.modules.interview.model.backendModel;

import java.util.List;

public record QuestionScore(
        int questionIndex,
        List<String> hitPoints,      // ① 先写证据
        List<String> missedPoints,   // ② 再写缺失
        int score,                   // ③ 最后给分
        String comment) {
}