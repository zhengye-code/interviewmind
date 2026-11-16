package com.interviewmind.modules.interview.repository;

import com.interviewmind.modules.interview.model.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {

    Optional<KnowledgeDocumentEntity> findBySource(String source);

    Optional<KnowledgeDocumentEntity> findByContentHash(String contentHash);

    List<KnowledgeDocumentEntity> findAllByOrderByIdAsc();
}