package com.interviewmind.modules.interview.controller;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.interview.model.entity.KnowledgeDocumentEntity;
import com.interviewmind.modules.interview.model.response.ReindexResponse;
import com.interviewmind.modules.interview.repository.KnowledgeDocumentRepository;
import com.interviewmind.modules.interview.service.RAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口：
 *   导入（扫 classpath 下的 knowledge/分类/xxx.md）
 *   查看已导入的文档
 *   手动检索（调试 RAG 质量用）
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    /** 向量库里 metadata 中"知识分类"的键名，必须和 Chunker 写入时一致 */
    private static final String META_CATEGORY = "kbCategory";
    private static final int DEFAULT_TOP_K = 3;
    private static final double DEFAULT_THRESHOLD = 0.6;

    private final RAGService ragService;
    private final KnowledgeDocumentRepository documentRepo;
    private final VectorStore vectorStore;

    /** 检索命中结果 */
    public record KnowledgeHit(String text, Double score, Map<String, Object> metadata) {
    }

    /** 扫描并导入知识文档；force=true 时内容没变也重新导入 */
    @PostMapping("/reindex")
    public Result<ReindexResponse> reindex(
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return Result.success(ragService.saveDocuments(force));
    }

    /** 已导入的文档列表 */
    @GetMapping("/documents")
    public Result<List<KnowledgeDocumentEntity>> documents() {
        return Result.success(documentRepo.findAllByOrderByIdAsc());
    }

    /** 按语义检索，可按知识分类过滤（如 REDIS / MYSQL / JAVA）；threshold 调低可看"原始"排序 */
    @GetMapping("/search")
    public Result<List<KnowledgeHit>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "topK", defaultValue = "" + DEFAULT_TOP_K) int topK,
            @RequestParam(value = "threshold", defaultValue = "" + DEFAULT_THRESHOLD) double threshold) {

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold);

        if (category != null && !category.isBlank()) {
            builder.filterExpression(new FilterExpressionBuilder()
                    .eq(META_CATEGORY, category.toUpperCase())
                    .build());
        }

        List<Document> documents = vectorStore.similaritySearch(builder.build());
        List<KnowledgeHit> hits = new ArrayList<>();
        if (documents != null) {
            for (Document document : documents) {
                hits.add(new KnowledgeHit(document.getText(), document.getScore(), document.getMetadata()));
            }
        }
        return Result.success(hits);
    }
}
