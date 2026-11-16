package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.AIsend.EvaluationItem;
import com.interviewmind.modules.interview.model.AIresult.InterviewEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InterviewGradingService {

    /** 每题最多取几段知识库材料 */
    private static final int RAG_TOP_K = 3;
    /** 相似度阈值：低于它的直接丢掉，避免把不相关内容塞进提示词 */
    private static final double RAG_SIMILARITY_THRESHOLD = 0.6;
    /** 向量库 metadata 里"知识分类"的键名，必须和 Chunker 写入时一致 */
    private static final String META_CATEGORY = "kbCategory";

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final PromptTemplate userPromptTemplate;
    private final VectorStore vectorStore;

    public InterviewGradingService(ChatClient chatClient, ResourceLoader resourceLoader, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.systemPrompt = load(resourceLoader, "classpath:prompts/interview-evaluation-system.st");
        this.userPromptTemplate = new PromptTemplate(
                load(resourceLoader, "classpath:prompts/interview-evaluation-user.st"));
        this.vectorStore = vectorStore;
    }

    private static String load(ResourceLoader resourceLoader, String location) {
        try {
            return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.LOADING_ERROR);
        }
    }

    /** 把每道题的作答材料（题目 / 回答 / 要点 / 知识库参考）拼成一段文字 */
    private String toFormattedString(List<EvaluationItem> items) {
        StringBuilder prompt = new StringBuilder();
        for (EvaluationItem item : items) {
            List<String> references = searchReferences(item);
            prompt.append(String.format("""
                            
                            --- 第 %d 题 ---
                            分类：%s
                            题目：%s
                            回答：%s
                            参考要点：%s
                            要点相关文档：%s
                            """,
                    item.questionIndex(),
                    item.categoryKey(),
                    item.questionText(),
                    item.answerText(),
                    joinPoints(item.expectedPoints()),
                    formatReferences(references)));
        }
        return prompt.toString();
    }

    /**
     * 按题目检索知识库。
     * 检索失败不能影响评分主流程 —— 记日志后当作"没有检索到"。
     */
    private List<String> searchReferences(EvaluationItem item) {
        String query = buildQuery(item);
        if (query.isBlank()) {
            log.warn("第 {} 题检索关键词为空，跳过 RAG", item.questionIndex());
            return List.of();
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(RAG_TOP_K)
                    .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                    .filterExpression(new FilterExpressionBuilder()
                            .eq(META_CATEGORY, item.categoryKey())
                            .build())
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }

            List<String> texts = new ArrayList<>(documents.size());
            for (Document document : documents) {
                if (document.getText() != null && !document.getText().isBlank()) {
                    texts.add(document.getText());
                }
            }
            log.debug("第 {} 题检索到 {} 段参考材料", item.questionIndex(), texts.size());
            return texts;

        } catch (Exception e) {
            log.warn("知识库检索失败，本题跳过 RAG 增强: questionIndex={}", item.questionIndex(), e);
            return List.of();
        }
    }

    /**
     * 检索词：优先用「参考要点」，题目兜底。
     *
     * 为什么不是用题目？
     *   题目是"场景叙述"（线上有个接口，QPS 涨到 2000……），
     *   知识库文档是"概念陈述"（联合索引让 WHERE 和 ORDER BY 都用上索引……）。
     *   两者语言风格不同，向量相似度天然偏低 —— 实测同一道题：
     *     用参考要点检索 → 0.727（命中且过阈值）
     *     用题目检索     → 0.555（全被阈值过滤）
     *   参考要点本来就是"知识点的陈述"，和文档同构，所以效果更好。
     */
    private String buildQuery(EvaluationItem item) {
        if (item.expectedPoints() != null && !item.expectedPoints().isEmpty()) {
            return String.join("；", item.expectedPoints());
        }
        return item.questionText() == null ? "" : item.questionText().trim();
    }

    /** 参考要点是列表，拼成一行比默认的 [a, b] 更好读 */
    private String joinPoints(List<String> points) {
        if (points == null || points.isEmpty()) {
            return "（无参考要点，按你的专业判断评分）";
        }
        return String.join("；", points);
    }

    /** 每段材料单独一行，避免多段粘连在一起 */
    private String formatReferences(List<String> references) {
        if (references == null || references.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            sb.append("\n  文档").append(i + 1).append("：").append(references.get(i));
        }
        return sb.toString();
    }

    public InterviewEvaluationResult getAIResponse(List<EvaluationItem> items, int totalQuestionNumber) {
        Map<String, Object> map = new HashMap<>();
        map.put("questionBlocks", toFormattedString(items));
        map.put("totalQuestions", totalQuestionNumber);

        String userText = userPromptTemplate.render(map);

        // 先打日志再调用：万一解析失败，也看得到实际发出去的提示词
        log.debug("评分提示词:\n{}", userText);

        InterviewEvaluationResult result = chatClient.prompt()
                .system(systemPrompt)
                .user(userText)
                .call()
                .entity(InterviewEvaluationResult.class);

        if (result == null || result.questionScores() == null || result.questionScores().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_GRADING_GENERATION_FAILED);
        }
        log.debug("AI 返回评分 {} 条", result.questionScores().size());
        return result;
    }
}
