package com.interviewmind.modules.resume.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.common.model.AsyncTaskStatus;
import com.interviewmind.modules.resume.model.ResumeAnalysisEntity;
import com.interviewmind.modules.resume.model.ResumeAnalysisResponse;
import com.interviewmind.modules.resume.model.ResumeAnalysisResult;
import com.interviewmind.modules.resume.model.ResumeEntity;
import com.interviewmind.modules.resume.repository.ResumeAnalysisRepository;
import com.interviewmind.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeAnalyzeService {
    private static final int MAX_ERROR_LENGTH=500;
    private final ResumeRepository resumeRepo;
    private final ResumeAnalysisRepository resumeAnalysisRepo;
    private final ResumeGradingService resumeGradingService;
    private final ObjectMapper objectMapper;
    public ResumeAnalysisResponse analyze(Long resumeId){
        ResumeEntity resumeEntity = resumeRepo.findById(resumeId)
                .orElseThrow(()-> new BusinessException(ErrorCode.RESUME_NOT_FOUND,"简历不存在,id="+resumeId));
        if(resumeEntity.getResumeText()==null||resumeEntity.getResumeText().trim().length()==0){
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED,"简历正文为空");
        }
        resumeEntity.setAnalyzeStatus(AsyncTaskStatus.PROCESSING);
        resumeRepo.save(resumeEntity);
        try {
            ResumeAnalysisResult result=resumeGradingService.analyze(resumeEntity.getResumeText());
            ResumeAnalysisEntity entity = new ResumeAnalysisEntity();
            entity.setResumeId(resumeId);
            entity.setOverallScore(result.overallScore());
            ResumeAnalysisResult.ScoreDetail detail = result.scoreDetail();
            if (detail != null) {
                entity.setProjectScore(detail.projectScore());
                entity.setSkillMatchScore(detail.skillMatchScore());
                entity.setContentScore(detail.contentScore());
                entity.setStructureScore(detail.structureScore());
                entity.setExpressionScore(detail.expressionScore());
            }
            entity.setSummary(result.summary());
            entity.setStrengthsJson(toJson(result.strengths()));
            entity.setSuggestionsJson(toJson(result.suggestions()));
            ResumeAnalysisEntity saved=resumeAnalysisRepo.save(entity);
            resumeEntity.setAnalyzeStatus(AsyncTaskStatus.COMPLETED);
            resumeEntity.setAnalyzeError(null);
            resumeRepo.save(resumeEntity);
            log.info("简历分析已入库：resumeId={},analysisId={},总分={}",saved.getId(),saved.getOverallScore());
            return toResponse(saved);
        } catch (Exception e) {
            resumeEntity.setAnalyzeStatus(AsyncTaskStatus.FAILED);
            resumeEntity.setAnalyzeError(truncate(e.getMessage()));
            resumeRepo.save(resumeEntity);
            log.error("简历分析失败,resumeId={}",resumeId,e);
            if(e instanceof BusinessException){
                throw (BusinessException)e;
            }
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED,"简历分析失败",e);
        }
    }

    public ResumeAnalysisResponse getLatest(Long resumeId){
        ResumeAnalysisEntity entity=resumeAnalysisRepo.findTopByResumeIdOrderByAnalyzedAtDesc(resumeId)
                .orElseThrow(()->new BusinessException(ErrorCode.RESUME_NOT_FOUND,"该简历没有分析结果"));
        return toResponse(entity);
    }

    private String toJson(List<String> json){
        try {
            return objectMapper.writeValueAsString(json == null ? List.of() : json);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED,"简历序列化失败",e);
        }
    }

    private List<String > fromJson(String json){
        if(json==null || json.trim().length()==0){
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>(){});
        } catch (Exception e) {
            log.warn("解析json失败，返回空列表,list={}",json);
            return List.of();
        }
    }

    private static String truncate(String str){
        if(str==null || str.trim().length()==0){
            return null;
        }
        if(str.length()>MAX_ERROR_LENGTH){
            return str.substring(0, MAX_ERROR_LENGTH);
        }
        return str;
    }

    private ResumeAnalysisResponse toResponse(ResumeAnalysisEntity resumeAnalysisEntity){
        return new ResumeAnalysisResponse(
                resumeAnalysisEntity.getResumeId(),
                resumeAnalysisEntity.getId(),
                nullToZero(resumeAnalysisEntity.getOverallScore()),
                nullToZero(resumeAnalysisEntity.getProjectScore()),
                nullToZero(resumeAnalysisEntity.getSkillMatchScore()),
                nullToZero(resumeAnalysisEntity.getContentScore()),
                nullToZero(resumeAnalysisEntity.getStructureScore()),
                nullToZero(resumeAnalysisEntity.getExpressionScore()),
                resumeAnalysisEntity.getSummary(),
                fromJson(resumeAnalysisEntity.getStrengthsJson()),
                fromJson(resumeAnalysisEntity.getSuggestionsJson()),
                resumeAnalysisEntity.getAnalyzedAt()
        );
    }

    /** DTO 里分数是 int，库里可能为 null，拆箱前必须兜底 */
    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
