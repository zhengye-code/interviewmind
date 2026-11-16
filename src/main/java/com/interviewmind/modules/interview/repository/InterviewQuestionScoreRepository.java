package com.interviewmind.modules.interview.repository;

import com.interviewmind.modules.interview.model.entity.InterviewQuestionScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionScoreRepository extends JpaRepository<InterviewQuestionScoreEntity, Long> {
    /** 一场面试的全部逐题评分 */
    List<InterviewQuestionScoreEntity> findBySessionId(Long sessionId);

    /** 某一道题的评分 */
    Optional<InterviewQuestionScoreEntity> findByQuestionId(Long questionId);

    /** 这一场评了几道题 */
    long countBySessionId(Long sessionId);
}
