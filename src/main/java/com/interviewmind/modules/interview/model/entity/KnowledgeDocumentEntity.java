package com.interviewmind.modules.interview.model.entity;

import com.interviewmind.modules.interview.model.status.KnowledgeImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 知识库文档登记：哪篇文档导过、切了几块、状态如何 */
@Getter
@Setter
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源路径，如 knowledge/redis/cache-pitfalls.md */
    @Column(name = "source", nullable = false, unique = true, length = 500)
    private String source;

    @Column(name = "title", length = 200)
    private String title;

    /** 分类，取目录名大写，如 REDIS */
    @Column(name = "category", length = 64)
    private String category;

    /** 内容 SHA-256，用于判断"内容有没有变" */
    @Column(name = "content_hash", nullable = false, unique = true, length = 64)
    private String contentHash;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KnowledgeImportStatus status = KnowledgeImportStatus.IMPORTING;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (importedAt == null) {
            importedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}