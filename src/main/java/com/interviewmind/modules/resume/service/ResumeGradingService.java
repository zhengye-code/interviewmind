package com.interviewmind.modules.resume.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.resume.model.ResumeAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class ResumeGradingService {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final PromptTemplate userPromptTemplate;
    public ResumeGradingService(ChatClient chatClient, ResourceLoader resourceLoader) throws IOException {
        this.chatClient = chatClient;
        this.systemPrompt=load(resourceLoader,"classpath:prompts/resume-analysis-system.st");
        this.userPromptTemplate=new PromptTemplate(load(resourceLoader,"classpath:prompts/resume-analysis-user.st"));
    }
    private static String load(ResourceLoader resourceLoader,String location) throws IOException {
        return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
    }
    public ResumeAnalysisResult analyze(String resumeText){
        String userText= userPromptTemplate.render(Map.of("resumeText",resumeText));
        try {
            ResumeAnalysisResult result=chatClient.prompt()
                    .system(systemPrompt)
                    .user(userText)
                    .call()
                    .entity(ResumeAnalysisResult.class);
            if(result==null){
                throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED,"AI未返回分析结果");
            }
            log.info("简历分析完成，总分={}",result.overallScore());
            return result;
        } catch (BusinessException e) {
            throw e;
        }catch (Exception e){
            log.error("简历分析AI调用失败：",e);
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED,"简历分析失败：",e);
        }
    }
}
