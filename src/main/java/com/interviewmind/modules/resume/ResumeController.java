package com.interviewmind.modules.resume;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.resume.model.ResumeUploadResponse;
import com.interviewmind.modules.resume.service.ResumeUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {
    private final ResumeUploadService resumeUploadService;
    @PostMapping(value = "/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadResponse> upload(@RequestParam("file")MultipartFile file){
        return Result.success(resumeUploadService.upload(file));
    }
}
