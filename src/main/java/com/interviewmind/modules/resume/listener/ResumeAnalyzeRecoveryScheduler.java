package com.interviewmind.modules.resume.listener;

import com.interviewmind.common.constant.AsyncTaskStreamConstants;
import com.interviewmind.common.model.AsyncTaskStatus;
import com.interviewmind.modules.resume.model.ResumeEntity;
import com.interviewmind.modules.resume.repository.ResumeAnalysisRepository;
import com.interviewmind.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAnalyzeRecoveryScheduler {
    private final ResumeRepository resumeRepo;
    private final ResumeAnalyzeProducer  resumeAnalyzeProducer;

    @Value("${app.async.resume-analyze.stuck-threshold-minutes:10}")
    private long stuckThresholdMinutes;

    @Scheduled(
            fixedDelayString = "${app.async.resume-analyze.recovery-interval-ms:60000}",
            initialDelayString = "${app.async.resume-analyze.recovery-initial-delay-ms:30000}")
    public void recoverStuckTasks(){
        LocalDateTime before=LocalDateTime.now();
        List<ResumeEntity> resumeEntities=resumeRepo.findStuckAnalyzeTasks(
                List.of(AsyncTaskStatus.PENDING,AsyncTaskStatus.PROCESSING),before
        );
        if(resumeEntities.size()==0){
            return;
        }
        log.warn("发现{}个卡住的任务，开始恢复……",resumeEntities.size());
        for(ResumeEntity resumeEntity:resumeEntities){
            try {
                recoverOne(resumeEntity);
            } catch (Exception e) {
                log.error("恢复任务失败，resumeId={}",resumeEntity.getId());
            }
        }
    }

    private void recoverOne(ResumeEntity resume){
        Long resumeId=resume.getId();
        if (resume.getAnalyzeRecoveryCount() >= AsyncTaskStreamConstants.MAX_RECOVERY_COUNT) {
            resume.setAnalyzeStatus(AsyncTaskStatus.FAILED);
            resume.setAnalyzeError("任务多次恢复失败，已停止自动重试");
            resume.setAnalyzeUpdatedAt(LocalDateTime.now());
            resumeRepo.save(resume);
            log.error("任务恢复次数耗尽，标记 FAILED: resumeId={}", resumeId);
            return;
        }
        resume.setAnalyzeRecoveryCount(resume.getAnalyzeRecoveryCount() + 1);
        resume.setAnalyzeStatus(AsyncTaskStatus.PENDING);
        resume.setAnalyzeError(null);
        resume.setAnalyzeUpdatedAt(LocalDateTime.now());
        resumeRepo.save(resume);
        boolean enqueued= resumeAnalyzeProducer.enqueue(resumeId);
        log.warn("重投卡住任务：resumeId={},recoveryCount={}",resumeId,resume.getAnalyzeRecoveryCount());
    }
}
