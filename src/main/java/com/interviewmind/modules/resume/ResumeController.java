package com.interviewmind.modules.resume;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.resume.model.ResumeAnalysisResponse;
import com.interviewmind.modules.resume.model.ResumeStatusResponse;
import com.interviewmind.modules.resume.model.ResumeUploadResponse;
import com.interviewmind.modules.resume.service.ResumeAnalyzeService;
import com.interviewmind.modules.resume.service.ResumeUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {
    private final ResumeUploadService resumeUploadService;
    private final ResumeAnalyzeService resumeAnalyzeService;
    @PostMapping(value = "/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadResponse> upload(@RequestParam("file")MultipartFile file){
        return Result.success(resumeUploadService.upload(file));
    }

    @PostMapping("/{id}/analyze")
    public Result<ResumeAnalysisResponse> analyzeResume(@PathVariable("id") Long id){
        return Result.success(resumeAnalyzeService.analyze(id));
    }

    @GetMapping("/{id}/analysis")
    public Result<ResumeAnalysisResponse> latestAnalysis(@PathVariable("id") Long id){
        return Result.success(resumeAnalyzeService.getLatest(id));
    }

    @GetMapping("/{id}/status")
    public Result<ResumeStatusResponse> status(@PathVariable("id") Long id){
        return Result.success(resumeAnalyzeService.getStatus(id));
    }
}
