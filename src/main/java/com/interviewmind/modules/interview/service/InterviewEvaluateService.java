package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.*;
import com.interviewmind.modules.interview.repository.InterviewQuestionRepository;
import com.interviewmind.modules.interview.repository.InterviewQuestionScoreRepository;
import com.interviewmind.modules.interview.repository.InterviewReportRepository;
import com.interviewmind.modules.interview.repository.InterviewSessionRepository;
import com.interviewmind.modules.interview.utils.TransformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewEvaluateService {
    private final InterviewSessionRepository sessionRepo;
    private final InterviewReportRepository reportRepo;
    private final InterviewQuestionScoreRepository questionScoreRepo;
    private final InterviewQuestionRepository questionRepo;
    private final InterviewGradingService gradingService;
    private final TransactionalService transactionalService;

    /**
     * 记录用的模型名（不是厂商名）。
     * 取 Spring AI 实际调用的 DeepSeek 模型；配置里没写时用具名默认值。
     * 注意：将来接入多 provider 后，这里要改成"按当前生效的 provider 取模型名"。
     */
    @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}")
    private String model;

    public InterviewEvaluationResponse evaluate(Long sessionId){
        InterviewSessionEntity sessionEntity = loadSession(sessionId);
        InterviewReportEntity reportEntity = reportRepo.findBySessionId(sessionId)
                .orElse(null);
        List<InterviewQuestionScoreEntity> scoreEntities=questionScoreRepo.findBySessionId(sessionId);
        List<InterviewQuestionEntity> questionEntities=
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        if(reportEntity != null){
            return toEvaluationResponse(reportEntity,sessionEntity,scoreEntities,questionEntities);
        }
        if(sessionEntity.getStatus()!= InterviewSessionStatus.COMPLETED){
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_STATE_INVALID);
        }
        List<EvaluationItem> items=new ArrayList<>();
        AtomicInteger index= new AtomicInteger(-1);
        questionEntities.forEach(entity->{
            EvaluationItem item=new EvaluationItem(
                    index.incrementAndGet(),
                    entity.getCategoryKey(),
                    entity.getQuestionText(),
                     TransformService.fromJsonToList(entity.getExpectedPoints(),String.class),
                    entity.getAnswerText()
            );
            items.add(item);
        });
        InterviewEvaluationResult result=gradingService.getAIResponse(
                items,sessionEntity.getTotalQuestions());

        if(result.questionScores().isEmpty()){
            throw new BusinessException(ErrorCode.INTERVIEW_GRADING_GENERATION_FAILED);
        }

        // 题号 → 题目主键。AI 可能返回本场不存在的题号，所以要能识别出来
        Map<Integer, Long> questionIdByIndex = new HashMap<>();
        for (InterviewQuestionEntity questionEntity : questionEntities) {
            questionIdByIndex.put(questionEntity.getQuestionIndex(), questionEntity.getId());
        }

        scoreEntities=new ArrayList<>();
        for(QuestionScore score:result.questionScores()){
            Long questionId=questionIdByIndex.get(score.questionIndex());
            if(questionId==null){
                log.warn("AI 返回了不存在的题号 {}，跳过该条评分: sessionId={}", score.questionIndex(), sessionId);
                continue;
            }
            InterviewQuestionScoreEntity scoreEntity=new InterviewQuestionScoreEntity();
            scoreEntity.setSessionId(sessionId);
            scoreEntity.setQuestionId(questionId);
            scoreEntity.setScore(clampScore(score.score()));
            scoreEntity.setComment(score.comment());
            scoreEntity.setHitPointsJson(TransformService.toJson(score.hitPoints()));
            scoreEntity.setMissedPointsJson(TransformService.toJson(score.missedPoints()));
            scoreEntities.add(scoreEntity);
        }
        if(scoreEntities.isEmpty()){
            throw new BusinessException(ErrorCode.INTERVIEW_GRADING_GENERATION_FAILED,
                    "AI 返回的评分都无法匹配到本场题目");
        }

        // 总分基于「已钳制」的逐题分计算，保证与入库数据一致
        reportEntity=new InterviewReportEntity();
        reportEntity.setSessionId(sessionId);
        reportEntity.setOverallScore(getOverallScore(scoreEntities));
        reportEntity.setModel(model);
        reportEntity.setStrengthsJson(TransformService.toJson(result.strengths()));
        reportEntity.setSuggestionsJson(TransformService.toJson(result.suggestions()));
        reportEntity.setSummary(result.summary());

        transactionalService.saveAllScoredQuestionsAndReports(reportEntity,scoreEntities);

        return toEvaluationResponse(reportEntity,sessionEntity,scoreEntities,questionEntities);
    }

    /**
     * 只读：查询已生成的评分报告。
     *
     * 和 evaluate 的区别：
     *   evaluate 会调 AI、会写库 —— 用 POST；
     *   本方法只读库 —— 用 GET，前端刷新页面 / 分享链接 / 浏览器预取都不会产生副作用。
     *
     * 还没评过时返回 3008，前端据此显示"去生成报告"的按钮。
     */
    @Transactional(readOnly = true)
    public InterviewEvaluationResponse getReport(Long sessionId){
        InterviewSessionEntity sessionEntity = loadSession(sessionId);
        InterviewReportEntity reportEntity = reportRepo.findBySessionId(sessionId)
                .orElseThrow(()->new BusinessException(ErrorCode.INTERVIEW_REPORT_NOT_FOUND,
                        "面试会话 " + sessionId + " 还没有评分报告"));
        List<InterviewQuestionScoreEntity> scoreEntities = questionScoreRepo.findBySessionId(sessionId);
        List<InterviewQuestionEntity> questionEntities =
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        return toEvaluationResponse(reportEntity,sessionEntity,scoreEntities,questionEntities);
    }

    private InterviewSessionEntity loadSession(Long sessionId){
        return sessionRepo.findById(sessionId)
                .orElseThrow(()->new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private List<QuestionScore> toScore(List<InterviewQuestionScoreEntity> scoreEntities,List<InterviewQuestionEntity> questionEntities){
        Map<Long, Integer> indexById=new HashMap<>();
        for(InterviewQuestionEntity questionEntity:questionEntities){
            indexById.put(questionEntity.getId(),questionEntity.getQuestionIndex());
        }
        List<QuestionScore> questionScores=new ArrayList<>();
        for(InterviewQuestionScoreEntity scoreEntity:scoreEntities){
            Integer questionIndex=indexById.get(scoreEntity.getQuestionId());
            if(questionIndex==null){
                log.warn("评分记录找不到对应题目，跳过: scoreId={}", scoreEntity.getId());
                continue;
            }
            questionScores.add(new QuestionScore(
                    questionIndex,
                    TransformService.fromJsonToList(scoreEntity.getHitPointsJson(),String.class),
                    TransformService.fromJsonToList(scoreEntity.getMissedPointsJson(),String.class),
                    scoreEntity.getScore()==null?0:scoreEntity.getScore(),
                    scoreEntity.getComment()
            ));
        }
        questionScores.sort(Comparator.comparingInt(QuestionScore::questionIndex));
        return questionScores;
    }

    private InterviewEvaluationResponse toEvaluationResponse(InterviewReportEntity reportEntity,
                                                             InterviewSessionEntity sessionEntity,
                                                             List<InterviewQuestionScoreEntity> scoreEntities,
                                                             List<InterviewQuestionEntity> questionEntities){
        return new InterviewEvaluationResponse(
                reportEntity.getSessionId(),
                reportEntity.getOverallScore(),
                scoreEntities.size(),
                reportEntity.getSummary(),
                TransformService.fromJsonToList(reportEntity.getStrengthsJson(),String.class),
                TransformService.fromJsonToList(reportEntity.getSuggestionsJson(),String.class),
                reportEntity.getModel(),
                reportEntity.getEvaluatedAt(),
                toScore(scoreEntities,questionEntities)
        );
    }

    /** 总分 = 逐题分（已钳制）的平均 × 10，四舍五入 */
    private int getOverallScore(List<InterviewQuestionScoreEntity> scoreEntities){
        int sum=0;
        for(InterviewQuestionScoreEntity scoreEntity:scoreEntities){
            sum+=scoreEntity.getScore();
        }
        return Math.round(sum * 10f / scoreEntities.size());
    }

    /** 分数钳制到 0-10，避免 AI 越界撞数据库 CHECK 约束导致整次评分失败 */
    private static int clampScore(int score){
        if(score<0){
            return 0;
        }
        return Math.min(score, 10);
    }

}
