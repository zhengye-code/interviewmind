package com.interviewmind.modules.interview.service;

import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import com.interviewmind.modules.interview.model.AIresult.GeneratedQuestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校对员：把 AI 返回的一批题目洗干净，合并进"已选好的题目"。
 *
 * 干四件事：
 *   1. 丢掉空题、丢掉自创分类
 *   2. 分类标识统一大写
 *   3. 去掉重复题（包括和已选题目重复）
 *   4. 每个分类不超过配额
 */
@Service
public class QuestionBatchSanitizeService {

    /** 判重时忽略的字符：空白、英文标点、中文标点 */
    private static final Pattern NOISE =
            Pattern.compile("[\\s\\p{Punct}，。？！；：、“”‘’（）《》、·…—－]");

    public List<GeneratedQuestion> merge(List<GeneratedQuestion> selected,
                                         List<GeneratedQuestion> raw,
                                         List<CategoryQuota> plan) {
        List<GeneratedQuestion> result = new ArrayList<>(selected == null ? List.of() : selected);

        // 每个分类还剩几个名额
        Map<String, Integer> remaining = new HashMap<>();
        for (CategoryQuota quota : plan) {
            remaining.put(quota.categoryKey(), quota.quota());
        }
        if (remaining.isEmpty()) {
            return result;
        }

        Set<String> seen = new HashSet<>();
        for (GeneratedQuestion question : result) {
            seen.add(normalizeText(question.question()));
            // 已经选好的题目同样占用名额，否则传入非空 selected 时会超发
            String categoryKey = normalizeKey(question.categoryKey());
            if (categoryKey != null && remaining.containsKey(categoryKey)) {
                remaining.put(categoryKey, remaining.get(categoryKey) - 1);
            }
        }

        if (raw == null) {
            return result;
        }

        for (GeneratedQuestion question : raw) {
            if (question == null || question.question() == null || question.question().isBlank()) {
                continue;
            }
            String categoryKey = normalizeKey(question.categoryKey());
            if (categoryKey == null || !remaining.containsKey(categoryKey)) {
                continue;
            }
            if (remaining.get(categoryKey) <= 0) {
                continue;
            }
            String normalized = normalizeText(question.question());
            if (normalized.isEmpty() || !seen.add(normalized)) {
                continue;
            }
            remaining.put(categoryKey, remaining.get(categoryKey) - 1);
            result.add(new GeneratedQuestion(
                    question.question().trim(),
                    categoryKey,
                    question.expectedPoints()));
        }
        return result;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim().toUpperCase(Locale.ROOT);
    }

    /** 压成"只留汉字字母数字"再比较，避免标点/空格差异造成重复 */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return NOISE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
