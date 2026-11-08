package com.interviewmind.modules.interview.repository;

import com.interviewmind.modules.interview.model.InterviewReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReportEntity, Long> {
    /** 一场面试的报告；也是"评过没有"的判断依据 */
    Optional<InterviewReportEntity> findBySessionId(Long sessionId);

    boolean existsBySessionId(Long sessionId);
}
