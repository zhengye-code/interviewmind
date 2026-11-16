package com.interviewmind.modules.interview.repository;

import com.interviewmind.modules.interview.model.entity.InterviewQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestionEntity, Long> {

    List<InterviewQuestionEntity> findBySessionIdOrderByQuestionIndexAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    boolean existsBySessionIdAndQuestionIndex(Long sessionId, int questionIndex);

    Optional<InterviewQuestionEntity> findFirstBySessionIdAndQuestionIndex(Long sessionId, int questionIndex);
}