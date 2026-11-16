package com.interviewmind.modules.interview.repository;

import com.interviewmind.modules.interview.model.entity.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {
}