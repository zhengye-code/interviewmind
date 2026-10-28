package com.interviewmind.modules.resume.model;

import com.interviewmind.common.model.AsyncTaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "resumes")
public class ResumeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_hash",nullable = false,unique = true,length = 64)
    private String fileHash;

    @Column(name = "original_filename",nullable = false,length = 255)
    private String originalFilename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type",length = 255)
    private String contentType;

    @Column(name = "storage_key",length = 500)
    private String storageKey;

    @Column(name = "resume_text",columnDefinition = "TEXT")
    private String resumeText;

    @Enumerated(EnumType.STRING)
    @Column(name = "analyze_status",length = 20)
    private AsyncTaskStatus analyzeStatus=AsyncTaskStatus.PENDING;

    @Column(name = "analyze_error", length = 500)
    private String analyzeError;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if(uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
