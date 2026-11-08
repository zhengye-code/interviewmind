package com.interviewmind.modules.interview.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "interview_reports")
@Entity
@Check(constraints = "overall_score >= 0 AND overall_score <= 100")
public class InterviewReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 一场面试只能有一份报告，这个唯一约束顺便当"评过没有"的判断依据 */
    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    /** 总分 0-100，由代码算（逐题平均 × 10），不是 AI 给的 */
    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** 优势（JSON 数组字符串） */
    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    /** 改进建议（JSON 数组字符串） */
    @Column(name = "suggestions_json", columnDefinition = "TEXT")
    private String suggestionsJson;

    /** 评这份报告用的模型，便于以后换模型时区分历史数据 */
    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = LocalDateTime.now();
        }
    }
}
