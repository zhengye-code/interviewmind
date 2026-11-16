package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.ai.AiChatService;
import com.interviewmind.modules.interview.model.AIresult.GeneratedQuestion;
import com.interviewmind.modules.interview.model.AIresult.GeneratedQuestionBatch;
import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import com.interviewmind.modules.interview.model.status.InterviewDifficulty;
import com.interviewmind.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionGenerateService {
    private final AiChatService aiChatService;
    private final InterviewSessionService interviewSessionService;
    private final ResumeRepository resumeRepo;
    private final ChatClient chatClient;
    private static final String rawPrompt;
    static{
        try {
            rawPrompt=loadTemplate();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_FOUND,"提示词加载失败",e);
        }
    }
    public List<GeneratedQuestion> generate(String systemPrompt,
                                            String skillName,
                                            InterviewDifficulty difficulty,
                                            int totalQuestions,
                                            List<CategoryQuota> plan,
                                            String resumeSection){

        Map<String,Object> map=new HashMap<>();
        map.put("skillName",skillName);
        map.put("difficulty",difficulty.name());
        map.put("totalQuestions",totalQuestions);
        map.put("quotaLines",quotaLines(plan));
        map.put("resumeSection",resumeSection==null?null:resumeSection);
        map.put("difficultyHint",difficultyHint(difficulty));
        String prompt=new PromptTemplate(rawPrompt).render(map);
        GeneratedQuestionBatch batch= chatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .call()
                .entity(GeneratedQuestionBatch.class);
        if(batch==null||batch.generatedQuestions().size()==0){
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,"AI未生成题目");
        }
        return batch.generatedQuestions();
    }

    private static String loadTemplate() throws Exception {
        return new ClassPathResource("prompts/interview-question-user.st")
                .getContentAsString(StandardCharsets.UTF_8);
    }
    private String difficultyHint(InterviewDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "偏基础，考察概念准确性与常见用法";
            case MEDIUM -> "中等，兼顾原理机制与真实工程场景";
            case HARD -> "偏难，聚焦边界条件、故障排查与设计取舍";
        };
    }
    private String quotaLines(List<CategoryQuota> plan) {
        return plan.stream()
                .filter(quota -> quota.quota() > 0)
                .map(quota -> "- " + quota.categoryKey() + "（" + quota.label() + "）：" + quota.quota() + " 道")
                .collect(Collectors.joining("\n"));
    }

}
