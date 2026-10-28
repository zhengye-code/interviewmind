package com.interviewmind.modules.resume.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.common.model.AsyncTaskStatus;
import com.interviewmind.infrastructure.file.FileHashService;
import com.interviewmind.infrastructure.file.FileValidationService;
import com.interviewmind.modules.resume.config.ResumeUploadProperties;
import com.interviewmind.modules.resume.model.ResumeEntity;
import com.interviewmind.modules.resume.model.ResumeUploadResponse;
import com.interviewmind.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadService {
    private final ResumeRepository resumeRepository;
    private final FileValidationService fileValidationService;
    private final FileHashService fileHashService;
    private final ResumeUploadProperties properties;
    public ResumeUploadResponse upload(MultipartFile file) {
        fileValidationService.validateFile(file,properties.getMaxSize()
                ,properties.getAllowedExtensions(),"简历");
        String hash= fileHashService.sha256Hex(file);
        Optional<ResumeEntity> resumeEntity = resumeRepository.findByFileHash(hash);
        if(resumeEntity.isPresent()){
            log.info("检测到重复简历，返回已有记录：id={}",resumeEntity.get().getId());
            return toResponse(resumeEntity.get(),true);
        }
        ResumeEntity entity = new ResumeEntity();
        entity.setFileHash(hash);
        entity.setFileSize(file.getSize());
        entity.setOriginalFilename(file.getOriginalFilename());
        entity.setAnalyzeStatus(AsyncTaskStatus.PENDING);
        entity.setContentType(file.getContentType());
        try {
            ResumeEntity savedEntity = resumeRepository.save(entity);
            log.info("简历已保存：id={},filename={},size={}"
                    ,savedEntity.getId(),savedEntity.getOriginalFilename(),savedEntity.getFileSize());
            return toResponse(savedEntity,false);
        }catch (DataIntegrityViolationException e){
            log.warn("唯一约束冲突，按重复处理，hash={}",hash);
            return resumeRepository.findByFileHash(hash)
                    .map(found -> toResponse(found, true))
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESUME_UPLOAD_FAILED, "简历保存失败"));
        }
    }

    private ResumeUploadResponse toResponse(ResumeEntity resumeEntity,boolean duplicate) {
        return new ResumeUploadResponse(
                resumeEntity.getId(),
                resumeEntity.getOriginalFilename(),
                resumeEntity.getFileSize() == null ? 0L : resumeEntity.getFileSize(),
               resumeEntity.getAnalyzeStatus() == null ? null : resumeEntity.getAnalyzeStatus().name(),
                duplicate);
    }
}
