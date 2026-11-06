package com.interviewmind.modules.interview.service;

import com.interviewmind.modules.interview.model.CategoryQuota;
import com.interviewmind.modules.interview.model.GeneratedQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 校对器是纯函数，直接 new 就能测，不需要 Spring 上下文 */
class QuestionBatchSanitizeServiceTest {

    private final QuestionBatchSanitizeService service = new QuestionBatchSanitizeService();

    /** 配额单：Java 2 道、MySQL 1 道 */
    private static List<CategoryQuota> plan() {
        return List.of(
                new CategoryQuota("JAVA", "Java", "CORE", 2),
                new CategoryQuota("MYSQL", "MySQL", "CORE", 1));
    }

    private static GeneratedQuestion q(String categoryKey, String question) {
        return new GeneratedQuestion(question, categoryKey, List.of("要点一"));
    }

    @Test
    void 自创分类会被丢弃() {
        List<GeneratedQuestion> result = service.merge(List.of(),
                List.of(q("JAVA", "HashMap 扩容机制"), q("GO", "goroutine 调度")),
                plan());

        assertThat(result).extracting(GeneratedQuestion::categoryKey).containsExactly("JAVA");
    }

    @Test
    void 超过配额的部分会被截断() {
        List<GeneratedQuestion> result = service.merge(List.of(),
                List.of(q("JAVA", "题一"), q("JAVA", "题二"), q("JAVA", "题三")),
                plan());

        assertThat(result).hasSize(2);
    }

    @Test
    void 完全相同的问题只保留一条() {
        List<GeneratedQuestion> result = service.merge(List.of(),
                List.of(q("JAVA", "HashMap 的扩容机制"), q("JAVA", "HashMap 的扩容机制")),
                plan());

        assertThat(result).hasSize(1);
    }

    @Test
    void 只差标点空白的问题也算重复() {
        List<GeneratedQuestion> result = service.merge(List.of(),
                List.of(q("JAVA", "HashMap 的扩容机制"), q("JAVA", "HashMap的扩容机制？")),
                plan());

        assertThat(result).hasSize(1);
    }

    @Test
    void 分类标识大小写会被归一化() {
        List<GeneratedQuestion> result = service.merge(List.of(),
                List.of(q("java", "HashMap 的扩容机制")),
                plan());

        assertThat(result).extracting(GeneratedQuestion::categoryKey).containsExactly("JAVA");
    }

    @Test
    void 与已选题目重复时不会重复加入() {
        List<GeneratedQuestion> selected = List.of(q("JAVA", "HashMap 的扩容机制"));

        List<GeneratedQuestion> result = service.merge(selected,
                List.of(q("JAVA", "HashMap 的扩容机制"), q("JAVA", "ConcurrentHashMap 的分段锁")),
                plan());

        assertThat(result).hasSize(2);
    }

    @Test
    void 已选题目会占用配额() {
        // 配额 JAVA=2，已选 1 道，新来的 2 道只能再收 1 道
        List<GeneratedQuestion> selected = List.of(q("JAVA", "已选第一题"));

        List<GeneratedQuestion> result = service.merge(selected,
                List.of(q("JAVA", "新题一"), q("JAVA", "新题二")),
                plan());

        assertThat(result).hasSize(2);
    }

    @Test
    void 空题目会被丢弃() {
        List<GeneratedQuestion> result = service.merge(List.of(),
                List.of(new GeneratedQuestion("   ", "JAVA", List.of("x")),
                        q("JAVA", "HashMap 的扩容机制")),
                plan());

        assertThat(result).hasSize(1);
    }

    @Test
    void 空配额单时原样返回已选题目() {
        List<GeneratedQuestion> selected = List.of(q("JAVA", "已选第一题"));

        List<GeneratedQuestion> result = service.merge(selected, List.of(q("JAVA", "新题")), List.of());

        assertThat(result).hasSize(1);
    }
}
