package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import com.interviewmind.modules.interview.model.entity.InterviewQuestionEntity;
import com.interviewmind.modules.interview.model.entity.InterviewSessionEntity;
import com.interviewmind.modules.interview.model.request.AnswerRequest;
import com.interviewmind.modules.interview.model.response.CurrentQuestionResponse;
import com.interviewmind.modules.interview.model.response.QuestionResponse;
import com.interviewmind.modules.interview.model.response.SummaryQuestionResponse;
import com.interviewmind.modules.interview.model.status.InterviewSessionStatus;
import com.interviewmind.modules.interview.repository.InterviewQuestionRepository;
import com.interviewmind.modules.interview.repository.InterviewSessionRepository;
import com.interviewmind.modules.interview.utils.TransformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnswerService {

    private final InterviewSessionRepository sessionRepo;
    private final InterviewQuestionRepository questionRepo;

    /** 查询当前该答哪一题 */
    @Transactional(readOnly = true)
    public CurrentQuestionResponse getCurrentQuestion(Long sessionId) {
        InterviewSessionEntity session = loadSession(sessionId);
        List<InterviewQuestionEntity> questions = loadQuestions(sessionId);
        return toResponse(currentQuestion(session, questions), session);
    }

    /**
     * 提交一题的回答，并推进到下一题；答完最后一题则结束会话。
     *
     * 必须整体成功或整体失败：要同时改 interview_questions 和 interview_sessions 两张表。
     */
    @Transactional
    public CurrentQuestionResponse answerQuestion(Long sessionId, int questionIndex, AnswerRequest answer) {
        InterviewSessionEntity session = loadSession(sessionId);
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_STATE_INVALID);
        }

        List<InterviewQuestionEntity> questions = loadQuestions(sessionId);
        int currentIndex = session.getCurrentQuestionIndex();

        // 顺序校验：客户端说"我答的是第几题"，必须和服务端的"当前题"一致
        if (questionIndex != currentIndex) {
            InterviewQuestionEntity target = (questionIndex >= 0 && questionIndex < questions.size())
                    ? questions.get(questionIndex)
                    : null;
            // 幂等：这一题已经答过了，说明是网络重试，直接返回当前进度，不再推进
            if (target != null && target.getAnsweredAt() != null) {
                log.info("重复提交已答过的题，忽略: sessionId={}, questionIndex={}", sessionId, questionIndex);
                return toResponse(questions.get(currentIndex), session);
            }
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_SEQUENCE_NOT_MATCH,
                    "答题顺序不匹配，当前应作答第 " + (currentIndex + 1) + " 题");
        }

        InterviewQuestionEntity answered = questions.get(currentIndex);
        answered.setAnsweredAt(LocalDateTime.now());
        answered.setAnswerText(answer.answerText());
        questionRepo.save(answered);

        // 已经是最后一题 → 结束这场面试
        if (currentIndex >= questions.size() - 1) {
            // 指针走完全程（0 → N），让 currentIndex == totalQuestions 成为"已结束"的统一标志，
            // 前端进度条才能显示 N/N；否则会停在 (N-1)/N
            session.setCurrentQuestionIndex(questions.size());
            session.setStatus(InterviewSessionStatus.COMPLETED);
            session.setFinishedAt(LocalDateTime.now());
            sessionRepo.save(session);
            return toResponse(answered, session);
        }

        // 否则推进到下一题，并给下一题打"已出题"时间戳（只打一次，反复刷新不重置计时）
        InterviewQuestionEntity next = questions.get(currentIndex + 1);
        if (next.getAskedAt() == null) {
            next.setAskedAt(LocalDateTime.now());
            questionRepo.save(next);
        }
        session.setCurrentQuestionIndex(currentIndex + 1);
        sessionRepo.save(session);
        return toResponse(next, session);
    }

    /** 列出本场全部题目（含已答过的） */
    @Transactional(readOnly = true)
    public List<SummaryQuestionResponse> getAllQuestions(Long sessionId) {
        InterviewSessionEntity session = loadSession(sessionId);
        List<SummaryQuestionResponse> responses = new ArrayList<>();
        for (InterviewQuestionEntity question : loadQuestions(sessionId)) {
            responses.add(new SummaryQuestionResponse(
                    sessionId,
                    question.getQuestionIndex(),
                    question.getQuestionText(),
                    question.getAnswerText(),
                    timeUsedSeconds(question)));
        }
        return responses;
    }

    /** 思考用时（秒）。没问过或没答过都返回 null，避免 NPE 和负数 */
    private Long timeUsedSeconds(InterviewQuestionEntity question) {
        if (question.getAskedAt() == null || question.getAnsweredAt() == null) {
            return null;
        }
        long seconds = ChronoUnit.SECONDS.between(question.getAskedAt(), question.getAnsweredAt());
        return Math.max(seconds, 0);
    }

    private InterviewSessionEntity loadSession(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private List<InterviewQuestionEntity> loadQuestions(Long sessionId) {
        List<InterviewQuestionEntity> questions =
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND);
        }
        return questions;
    }

    /** 取当前题；下标越界（异常数据）时退到最后一道，避免 IndexOutOfBounds 变成 500 */
    private InterviewQuestionEntity currentQuestion(InterviewSessionEntity session,
                                                    List<InterviewQuestionEntity> questions) {
        int index = session.getCurrentQuestionIndex();
        if (index < 0 || index >= questions.size()) {
            return questions.get(questions.size() - 1);
        }
        return questions.get(index);
    }

    private CurrentQuestionResponse toResponse(InterviewQuestionEntity question,
                                               InterviewSessionEntity session) {
        Map<String, String> labels = TransformService.categoryLabels(
                TransformService.fromJsonToList(session.getPlanJson(), CategoryQuota.class));
        return new CurrentQuestionResponse(
                session.getId(),
                session.getStatus().name(),
                session.getCurrentQuestionIndex(),
                session.getTotalQuestions(),
                new QuestionResponse(
                        question.getId(),
                        question.getQuestionIndex(),
                        question.getCategoryKey(),
                        labels.getOrDefault(question.getCategoryKey(), question.getCategoryKey()),
                        question.getDifficulty() == null ? null : question.getDifficulty().name(),
                        question.getQuestionText()));
    }
}
