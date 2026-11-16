package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import com.interviewmind.modules.interview.model.entity.InterviewSessionEntity;
import com.interviewmind.modules.interview.model.request.CreateSessionRequest;
import com.interviewmind.modules.interview.model.response.SessionResponse;
import com.interviewmind.modules.interview.model.status.InterviewDifficulty;
import com.interviewmind.modules.interview.model.status.InterviewSessionStatus;
import com.interviewmind.modules.interview.repository.InterviewSessionRepository;
import com.interviewmind.modules.interview.model.backendModel.SkillDefinition;
import com.interviewmind.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {
    public static final int DEFAULT_TOTAL_QUESTIONS = 10;

    private final InterviewSkillService skillService;
    private final QuestionPlanService questionPlanService;
    private final InterviewSessionRepository sessionRepo;
    private final ResumeRepository resumeRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public SessionResponse create(CreateSessionRequest request) {
        SkillDefinition skill=skillService.getSkillDefinition(request.skillId());
        if(request.resumeId()!=null&&!resumeRepo.existsById(request.resumeId())){
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND,"简历不存在，id="+request.resumeId());
        }
        int totalQuestions=request.totalQuestions() == null
                ? DEFAULT_TOTAL_QUESTIONS
                : request.totalQuestions();
        InterviewDifficulty difficulty=InterviewDifficulty.fromCode(request.difficulty());
        List<CategoryQuota> plan=questionPlanService.plan(totalQuestions,skill.getCategories());
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setSkillId(skill.getId());
        entity.setResumeId(request.resumeId());
        entity.setStatus(InterviewSessionStatus.IN_PROGRESS);
        entity.setDifficulty(difficulty);
        entity.setTotalQuestions(totalQuestions);
        entity.setCurrentQuestionIndex(0);
        entity.setPlanJson(toJson(plan));
        InterviewSessionEntity saved = sessionRepo.save(entity);
        log.info("面试会话已创建: sessionId={}, skillId={}, resumeId={}, 题量={}, 配额={}",
                saved.getId(), saved.getSkillId(), saved.getResumeId(), totalQuestions, plan);

        return toResponse(saved, skill.getDisplayName());
    }

    @Transactional(readOnly = true)
    public SessionResponse get(Long sessionId) {
        InterviewSessionEntity entity = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "面试会话不存在,id=" + sessionId));
        String skillName=resolveSkillName(entity.getSkillId());
        return  toResponse(entity, skillName);
    }
    private String resolveSkillName(String skillId) {
        try {
            return skillService.getSkillDefinition(skillId).getDisplayName();
        } catch (BusinessException e) {
            log.warn("会话引用的 skill 已不存在: skillId={}", skillId);
            return skillId;
        }
    }
    private SessionResponse toResponse(InterviewSessionEntity entity,String skillName) {
        return new SessionResponse(
                entity.getId(),
                entity.getSkillId(),
                skillName,
                entity.getResumeId(),
                entity.getStatus().name(),
                entity.getDifficulty().name(),
                entity.getTotalQuestions(),
                entity.getCurrentQuestionIndex(),
                fromJson(entity.getPlanJson()),
                entity.getCreatedAt());
    }
    private String toJson(List<CategoryQuota> plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "出题配额序列化失败", e);
        }
    }

    private List<CategoryQuota> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CategoryQuota>>() {});
        } catch (JacksonException e) {
            log.warn("解析出题配额 JSON 失败,json={}", json);
            return List.of();
        }
    }
}
