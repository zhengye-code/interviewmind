package com.interviewmind.modules.resume.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name="resume_analyses")
public class ResumeAnalysisEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联简历 id。这里只存 id，不用 @ManyToOne，避免懒加载序列化问题 */
    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    private Integer overallScore;
    private Integer projectScore;
    private Integer skillMatchScore;
    private Integer contentScore;
    private Integer structureScore;
    private Integer expressionScore;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** List<String> 序列化后的 JSON */
    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "suggestions_json", columnDefinition = "TEXT")
    private String suggestionsJson;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }
}
