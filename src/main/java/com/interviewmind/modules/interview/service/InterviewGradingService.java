package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.EvaluationItem;
import com.interviewmind.modules.interview.model.InterviewEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InterviewGradingService {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final PromptTemplate userPromptTemplate;

    public InterviewGradingService(ChatClient chatClient, ResourceLoader resourceLoader) {
        this.chatClient = chatClient;
        this.systemPrompt = load(resourceLoader,"classpath:prompts/interview-evaluation-system.st");
        this.userPromptTemplate = new PromptTemplate(
                load(resourceLoader,"classpath:prompts/interview-evaluation-user.st"));
    }
    private static String load(ResourceLoader resourceLoader, String location) {
        try {
            return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.LOADING_ERROR);
        }
    }

    private String toFormattedString(List<EvaluationItem> items) {
        StringBuilder sb = new StringBuilder();
        for (EvaluationItem item : items) {

            sb.append(String.format(
                            """
                                    
                            --- 第 %d 题 ---
                            分类：%s
                            题目：%s
                            回答：%s
                            参考要点：%s"""
                    ,item.questionIndex(),item.categoryKey(),item.questionText(),item.answerText(),joinPoints(item.expectedPoints())
            ));
        }
        return sb.toString();
    }

    /** 参考要点是列表，拼成一行比默认的 [a, b] 更好读 */
    private String joinPoints(List<String> points) {
        if (points == null || points.isEmpty()) {
            return "（无参考要点，按你的专业判断评分）";
        }
        return String.join("；", points);
    }

    public InterviewEvaluationResult getAIResponse(List<EvaluationItem> items, int totalQuestionNumber){
        Map<String,Object> map = new HashMap<>();
        map.put("questionBlocks",toFormattedString(items));
        map.put("totalQuestions",totalQuestionNumber);
        String userText = userPromptTemplate.render(map);
        InterviewEvaluationResult result = chatClient.prompt()
                .system(systemPrompt)
                .user(userText)
                .call()
                .entity(InterviewEvaluationResult.class);
        if(result==null||result.questionScores()==null||result.questionScores().size()==0){
            throw new BusinessException(ErrorCode.INTERVIEW_GRADING_GENERATION_FAILED);
        }
        log.debug("评分提示词:\n{}", userText);
        log.debug("AI 返回评分 {} 条", result.questionScores().size());
        return result;
    }
}
