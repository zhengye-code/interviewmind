package com.interviewmind.modules.interview.service;

import com.interviewmind.modules.interview.model.entity.InterviewQuestionEntity;
import com.interviewmind.modules.interview.model.entity.InterviewQuestionScoreEntity;
import com.interviewmind.modules.interview.model.entity.InterviewReportEntity;
import com.interviewmind.modules.interview.model.entity.KnowledgeDocumentEntity;
import com.interviewmind.modules.interview.repository.InterviewQuestionRepository;
import com.interviewmind.modules.interview.repository.InterviewQuestionScoreRepository;
import com.interviewmind.modules.interview.repository.InterviewReportRepository;
import com.interviewmind.modules.interview.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionalService {
    private final InterviewQuestionRepository questionRepo;
    private final InterviewReportRepository reportRepo;
    private final InterviewQuestionScoreRepository questionScoreRepo;
    private final KnowledgeDocumentRepository knowledgeDocumentRepo;
    @Transactional
    public void saveAllQuestions(List<InterviewQuestionEntity> questionEntities){
        questionRepo.saveAll(questionEntities);
    }
    @Transactional
    public void saveReport(InterviewReportEntity reportEntity){
        reportRepo.save(reportEntity);
    }
    @Transactional
    public void saveAllScoredQuestions(List<InterviewQuestionScoreEntity> scoreEntities){
        questionScoreRepo.saveAll(scoreEntities);
    }
    @Transactional
    public void saveAllScoredQuestionsAndReports(InterviewReportEntity reportEntity, List<InterviewQuestionScoreEntity> scoreEntities){
        questionScoreRepo.saveAll(scoreEntities);
        reportRepo.save(reportEntity);
    }
    @Transactional
    public void saveKnowledgeDocument(KnowledgeDocumentEntity knowledgeDocumentEntity){
        knowledgeDocumentRepo.save(knowledgeDocumentEntity);
    }
}
