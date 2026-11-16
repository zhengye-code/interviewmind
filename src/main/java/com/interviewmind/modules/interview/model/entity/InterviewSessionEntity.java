package com.interviewmind.modules.interview.model.entity;

import com.interviewmind.modules.interview.model.status.InterviewSessionStatus;
import com.interviewmind.modules.interview.model.status.InterviewDifficulty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "interview_sessions")
public class InterviewSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false, length = 64)
    private String skillId;

    /** 关联的简历，可为空：支持不基于简历的通用面试 */
    @Column(name = "resume_id")
    private Long resumeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InterviewSessionStatus status = InterviewSessionStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private InterviewDifficulty difficulty = InterviewDifficulty.MEDIUM;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    /** 当前待答题目的下标，0 表示还没答第一题 */
    @Column(name = "current_question_index", nullable = false)
    private int currentQuestionIndex = 0;

    /** 出题配额的 JSON 快照，创建时算定后不再变化 */
    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
    private String planJson;

    /** 乐观锁版本号，Hibernate 自动维护 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}