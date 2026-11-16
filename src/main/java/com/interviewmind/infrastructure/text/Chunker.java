package com.interviewmind.infrastructure.text;

import com.interviewmind.modules.interview.model.entity.KnowledgeDocumentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class Chunker {
    public List<Document> chunkBySubtitle(KnowledgeDocumentEntity documentEntity,
                                                Resource document,
                                                int chunkMaxSize,
                                                int overlap) {
        if(chunkMaxSize<=0){
            log.warn("chunkMaxSize not valid while chunking");
            return List.of();
        }
        overlap = Math.max(0, Math.min(overlap, chunkMaxSize / 4));
        MarkdownDocumentReaderConfig config=MarkdownDocumentReaderConfig.builder()
                .withIncludeBlockquote(true)
                .withIncludeCodeBlock(true)
                .withHorizontalRuleCreateDocument(false)
                .build();
        MarkdownDocumentReader reader=new MarkdownDocumentReader(document,config);
        List<Document> chunks = new ArrayList<>(reader.get());
        Map<String,Object> map = Map.of(
                "kbCategory",documentEntity.getCategory(),
                "source",documentEntity.getSource()
        );
        int partNumber = 1;
        List<Document> newChunks = new ArrayList<>();
        for(int i=0;i<chunks.size();i++){
            if(chunks.get(i).getText()==null||chunks.get(i).getText().isEmpty()){
                continue;
            }
            Map<String,Object> metadata =chunks.get(i).getMetadata();
            metadata.putAll(map);
            if(chunks.get(i).getText().length()>chunkMaxSize){
                String part=chunks.get(i).getText();
                int partPage =1;
                metadata.put("isPart",true);
                metadata.put("partNumber",partNumber);
                partNumber++;
                metadata.put("partPage", partPage);
                chunks.set(i,new Document(part.substring(0,chunkMaxSize),metadata));
                for (int j = chunkMaxSize-overlap; j < part.length(); j+=(chunkMaxSize-overlap)) {
                    partPage++;
                    metadata.put("partPage",partPage);
                    newChunks.add(new Document(part.substring(j,j+chunkMaxSize>part.length()?part.length():j+chunkMaxSize),metadata));
                }
            }else{
                metadata.put("isPart",false);
                chunks.set(i,new Document(chunks.get(i).getText(),metadata));
            }
        }
        chunks.addAll(newChunks);
        return chunks;
    }
}
