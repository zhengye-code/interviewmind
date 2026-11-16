package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.infrastructure.file.FileHashService;
import com.interviewmind.infrastructure.text.Chunker;
import com.interviewmind.modules.interview.model.entity.KnowledgeDocumentEntity;
import com.interviewmind.modules.interview.model.response.ReindexResponse;
import com.interviewmind.modules.interview.model.status.KnowledgeImportStatus;
import com.interviewmind.modules.interview.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {
    private final KnowledgeDocumentRepository knowledgeRepo;
    private final FileHashService fileHashService;
    private final Chunker chunker;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final TransactionalService transactionalService;
    private final VectorStore vectorStore;
    private static final String KNOWLEDGE_GLOB = "classpath*:knowledge/*/*.md";

    public ReindexResponse saveDocuments(boolean force) {
        Resource[] resources;
        int skip=0;
        int failed=0;
        int totalChunks=0;
        int imported=0;
        try {
            resources=resourceResolver.getResources(KNOWLEDGE_GLOB);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.LOADING_ERROR);
        }
        for (Resource resource : resources) {
            try {
                InputStream inputStream = resource.getInputStream();
                String content=new String(inputStream.readAllBytes(),StandardCharsets.UTF_8);
                String hash=fileHashService.sha256Hex(content);
                URL url=resource.getURL();
                KnowledgeDocumentEntity documentEntity=knowledgeRepo.findBySource(url.toString().substring(url.toString().lastIndexOf("knowledge"))).orElse(new  KnowledgeDocumentEntity());
                if(documentEntity.getContentHash()!=null ) {
                    if(!force||documentEntity.getContentHash().equals(hash)) {
                        skip++;
                        continue;
                    }
                    vectorStore.delete(new FilterExpressionBuilder().eq("source",documentEntity.getSource()).build());
                }
                documentEntity.setContentHash(hash);
                documentEntity.setCategory(getCategory(resource.getURL()));
                documentEntity.setSource(url.toString().substring(url.toString().lastIndexOf("knowledge")));
                // 标题取第一行的文字（去掉 markdown 的 # 前缀）；取不到就用文件名
                String firstLine = content.lines().findFirst().orElse("")
                        .replaceFirst("^#\\s*", "").trim();
                documentEntity.setTitle(firstLine.isEmpty()
                        ? url.getPath().substring(url.getPath().lastIndexOf("/") + 1)
                        : firstLine);
                List<Document> chunks=chunker.chunkBySubtitle(documentEntity, resource,512,50);
                vectorStore.add(chunks);
                totalChunks+=chunks.size();
                documentEntity.setChunkCount(chunks.size());
                documentEntity.setStatus(KnowledgeImportStatus.READY);
                transactionalService.saveKnowledgeDocument(documentEntity);
                imported++;
            } catch (Exception e) {
                log.error("文件导入数据库失败，文件名：{}，错误：{}",resource.getFilename(),e);
                failed++;
            }
        }
        return new ReindexResponse(imported, skip, failed, totalChunks);
    }
    private String getCategory(URL url) {
        String[] splits=url.getPath().split("/");
        if(splits[splits.length-2].equals("knowledge")){
            return "unsorted";
        }
        return splits[splits.length-2].toUpperCase();
    }
}
