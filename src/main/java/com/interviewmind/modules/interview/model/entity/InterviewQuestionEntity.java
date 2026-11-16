package com.interviewmind.modules.interview.model.entity;

import com.interviewmind.modules.interview.model.status.InterviewDifficulty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "interview_questions")
public class InterviewQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 同样只存 id，不用 @ManyToOne（和 ResumeAnalysisEntity 保持一致的理由） */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 第几题，从 0 开始，和 currentQuestionIndex 对齐 */
    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private InterviewDifficulty difficulty;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** 参考答案要点（JSON 数组字符串），用于后续评估 */
    @Column(name = "expected_points", columnDefinition = "TEXT")
    private String expectedPoints;

    /** 出题时间；为 null 表示还没出到这一题 */
    @Column(name = "asked_at")
    private LocalDateTime askedAt;
    /** 用户的回答；null 表示还没答 */
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    /** 提交回答的时间；null 表示这题还没答过 */
    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}