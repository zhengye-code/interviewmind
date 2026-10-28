package com.interviewmind.modules.resume.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.common.model.AsyncTaskStatus;
import com.interviewmind.infrastructure.file.DocumentParseService;
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
    private final DocumentParseService documentParseService;
    private final ResumeUploadProperties properties;

    public ResumeUploadResponse upload(MultipartFile file) {
        // 1. 扩展名 + 大小 + 非空（快速失败，给用户友好的提示）
        fileValidationService.validateFile(file, properties.getMaxSize(),
                properties.getAllowedExtensions(), "简历");
        String extension = fileValidationService.extensionOf(file.getOriginalFilename());

        // 2. 探测真实类型（只用文件内容，不信任客户端声明的 Content-Type 和文件名）
        String detectedContentType = documentParseService.detectContentType(file);
        fileValidationService.validateContentType(detectedContentType,
                properties.getAllowedContentTypes(), "简历");
        fileValidationService.validateExtensionMatchesContent(extension,
                detectedContentType, "简历");

        // 3. 文件指纹
        String fileHash = fileHashService.sha256Hex(file);

        // 4. 去重
        Optional<ResumeEntity> existing = resumeRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            log.info("检测到重复简历，返回已有记录：id={}", existing.get().getId());
            return toResponse(existing.get(), true);
        }

        // 5. 解析正文
        long parseStart = System.currentTimeMillis();
        String resumeText = documentParseService.parseToText(file);
        if (resumeText == null || resumeText.isBlank()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED,
                    "无法从文件中提取文本，请确认不是扫描版 PDF");
        }
        log.info("简历解析完成：filename={}，文本长度={}，耗时={}ms",
                file.getOriginalFilename(), resumeText.length(), System.currentTimeMillis() - parseStart);

        // 6. 入库
        ResumeEntity entity = new ResumeEntity();
        entity.setFileHash(fileHash);
        entity.setOriginalFilename(file.getOriginalFilename());
        entity.setFileSize(file.getSize());
        entity.setContentType(detectedContentType);   // 存探测出的真实类型，不是客户端声明的
        entity.setResumeText(resumeText);
        entity.setAnalyzeStatus(AsyncTaskStatus.PENDING);

        try {
            ResumeEntity savedEntity = resumeRepository.save(entity);
            log.info("简历已保存：id={}, filename={}, size={}B",
                    savedEntity.getId(), savedEntity.getOriginalFilename(), savedEntity.getFileSize());
            return toResponse(savedEntity, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("唯一约束冲突，按重复处理，hash={}", fileHash);
            return resumeRepository.findByFileHash(fileHash)
                    .map(found -> toResponse(found, true))
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESUME_UPLOAD_FAILED, "简历保存失败"));
        }
    }

    private ResumeUploadResponse toResponse(ResumeEntity resumeEntity, boolean duplicate) {
        return new ResumeUploadResponse(
                resumeEntity.getId(),
                resumeEntity.getOriginalFilename(),
                resumeEntity.getFileSize() == null ? 0L : resumeEntity.getFileSize(),
                resumeEntity.getAnalyzeStatus() == null ? null : resumeEntity.getAnalyzeStatus().name(),
                duplicate);
    }
}
