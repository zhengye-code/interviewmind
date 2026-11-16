package com.interviewmind.modules.interview.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Check(constraints = "score >= 0 AND score <= 10")
@Table(name = "interview_question_scores")
public class InterviewQuestionScoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 冗余字段：方便"查一场面试的全部评分"，不用先查题目表 */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 一道题只能有一条评分 */
    @Column(name = "question_id", nullable = false, unique = true)
    private Long questionId;

    /** 0-10 分 */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** 命中的参考要点（JSON 数组字符串） */
    @Column(name = "hit_points_json", columnDefinition = "TEXT")
    private String hitPointsJson;

    /** 遗漏的参考要点（JSON 数组字符串） */
    @Column(name = "missed_points_json", columnDefinition = "TEXT")
    private String missedPointsJson;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
