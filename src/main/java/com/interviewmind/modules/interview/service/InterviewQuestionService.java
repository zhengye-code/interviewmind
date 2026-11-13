package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.CategoryQuota;
import com.interviewmind.modules.interview.model.GeneratedQuestion;
import com.interviewmind.modules.interview.model.InterviewQuestionEntity;
import com.interviewmind.modules.interview.model.InterviewSessionEntity;
import com.interviewmind.modules.interview.model.InterviewSessionStatus;
import com.interviewmind.modules.interview.model.QuestionResponse;
import com.interviewmind.modules.interview.repository.InterviewQuestionRepository;
import com.interviewmind.modules.interview.repository.InterviewSessionRepository;
import com.interviewmind.modules.interview.utils.TransformService;
import com.interviewmind.modules.resume.model.ResumeEntity;
import com.interviewmind.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewQuestionService {

    private final InterviewQuestionRepository questionRepo;
    private final InterviewSessionRepository sessionRepo;
    private final QuestionGenerateService questionGenerateService;
    private final InterviewSkillService skillService;
    private final ResumeRepository resumeRepo;
    private final QuestionBatchSanitizeService sanitizeService;
    private final TransactionalService transactionalService;

    /**
     * 出题并落库。幂等：这场面试已经有题就直接返回，不重复调用 AI（一次要 30 多秒）。
     *
     * 注意：方法本身不加 @Transactional —— 里面有一次十几秒的 AI 调用，
     * 包在事务里会长时间占用数据库连接。真正需要事务的只有最后那次批量写入。
     */
    public List<QuestionResponse> generate(Long sessionId) {
        InterviewSessionEntity sessionEntity = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        if (sessionEntity.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_STATE_INVALID);
        }
        if (sessionEntity.getPlanJson() == null || sessionEntity.getPlanJson().isBlank()) {
            return List.of();
        }

        List<InterviewQuestionEntity> questionEntities =
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(sessionId);

        if (questionEntities.isEmpty()) {
            questionEntities = createQuestions(sessionEntity);
        }
        return toResponse(questionEntities, sessionEntity);
    }

    public List<QuestionResponse> getQuestions(Long sessionId) {
        InterviewSessionEntity sessionEntity = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        List<InterviewQuestionEntity> questionEntities =
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        if (questionEntities.isEmpty()) {
            return List.of();
        }
        return toResponse(questionEntities, sessionEntity);
    }

    /** 调 AI 出题 → 清洗 → 校准题数 → 一个事务里写库 */
    private List<InterviewQuestionEntity> createQuestions(InterviewSessionEntity sessionEntity) {
        List<CategoryQuota> plan = TransformService.fromJsonToList(sessionEntity.getPlanJson(), CategoryQuota.class);

        List<GeneratedQuestion> generated = sanitizeService.merge(
                List.of(),
                questionGenerateService.generate(
                        skillService.getSkillBodies(sessionEntity.getSkillId()),
                        skillService.getSkillDefinition(sessionEntity.getSkillId()).getName(),
                        sessionEntity.getDifficulty(),
                        sessionEntity.getTotalQuestions(),
                        plan,
                        buildResumeSection(sessionEntity.getResumeId())),
                plan);

        List<InterviewQuestionEntity> entities = new ArrayList<>(generated.size());
        AtomicInteger index = new AtomicInteger(-1);
        for (GeneratedQuestion question : generated) {
            InterviewQuestionEntity entity = new InterviewQuestionEntity();
            entity.setSessionId(sessionEntity.getId());
            entity.setQuestionIndex(index.incrementAndGet());
            entity.setCategoryKey(question.categoryKey());
            entity.setQuestionText(question.question());
            entity.setExpectedPoints(TransformService.toJson(question.expectedPoints()));
            entity.setDifficulty(sessionEntity.getDifficulty());
            // 只有第 0 题算"已经问出来了"，其余等答到那一题时再打时间戳
            if (index.get() == 0) {
                entity.setAskedAt(LocalDateTime.now());
            }
            entities.add(entity);
        }

        transactionalService.saveAllQuestions(entities);

        // 校准：AI 实际给几道就以几道为准，避免"会话说 10 题、题库只有 8 行"
        if (sessionEntity.getTotalQuestions() != entities.size()) {
            log.warn("AI 实际给出 {} 道，计划 {} 道，以实际为准: sessionId={}",
                    entities.size(), sessionEntity.getTotalQuestions(), sessionEntity.getId());
            sessionEntity.setTotalQuestions(entities.size());
            sessionRepo.save(sessionEntity);
        }
        return entities;
    }

    /** 简历正文 → 可拼进提示词的一段话；没有简历就是空字符串 */
    private String buildResumeSection(Long resumeId) {
        if (resumeId == null) {
            return "";
        }
        ResumeEntity resume = resumeRepo.findById(resumeId).orElse(null);
        if (resume == null || resume.getResumeText() == null || resume.getResumeText().isBlank()) {
            return "";
        }
        return "候选人简历（可用于生成结合真实项目的题目）：\n"
                + "--- 简历开始 ---\n" + resume.getResumeText().trim() + "\n--- 简历结束 ---";
    }

    private List<QuestionResponse> toResponse(List<InterviewQuestionEntity> questionEntities,
                                              InterviewSessionEntity sessionEntity) {
        Map<String, String> labels = TransformService.categoryLabels(
                TransformService.fromJsonToList(sessionEntity.getPlanJson(), CategoryQuota.class));

        List<QuestionResponse> questionResponses = new ArrayList<>(questionEntities.size());
        for (InterviewQuestionEntity question : questionEntities) {
            questionResponses.add(new QuestionResponse(
                    question.getId(),
                    question.getQuestionIndex(),
                    question.getCategoryKey(),
                    labels.getOrDefault(question.getCategoryKey(), question.getCategoryKey()),
                    question.getDifficulty() == null ? null : question.getDifficulty().name(),
                    question.getQuestionText()));
        }
        return questionResponses;
    }
}
