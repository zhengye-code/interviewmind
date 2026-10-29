package com.interviewmind.modules.resume.repository;

import com.interviewmind.common.model.AsyncTaskStatus;
import com.interviewmind.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<ResumeEntity,Long> {
    Optional<ResumeEntity> findByFileHash(String fileHash);
    /**
     * 查询「状态停在 PENDING/PROCESSING 且超过阈值没有更新」的任务。
     * 用一个统一阈值即可：AI 分析正常 10~30 秒，10 分钟远超它。
     */
    @Query("""
            select r from ResumeEntity r
             where r.analyzeStatus in :statuses
               and (r.analyzeUpdatedAt is null or r.analyzeUpdatedAt < :before)
            """)
    List<ResumeEntity> findStuckAnalyzeTasks(@Param("statuses") Collection<AsyncTaskStatus> statuses,
                                             @Param("before") LocalDateTime before);
}
