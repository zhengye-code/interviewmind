package com.interviewmind.modules.resume.repository;

import com.interviewmind.modules.resume.model.ResumeAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {
    Optional<ResumeAnalysisEntity> findTopByResumeIdOrderByAnalyzedAtDesc(Long resumeId);
}
