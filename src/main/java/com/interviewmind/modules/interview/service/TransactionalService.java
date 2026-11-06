package com.interviewmind.modules.interview.service;

import com.interviewmind.modules.interview.model.InterviewQuestionEntity;
import com.interviewmind.modules.interview.repository.InterviewQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionalService {
    private final InterviewQuestionRepository questionRepo;
    @Transactional
    public void saveAll(List<InterviewQuestionEntity> questionEntities){
        questionRepo.saveAll(questionEntities);
    }
}
