package com.interviewmind.modules.resume.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.resume")
public class ResumeUploadProperties {
    private Long maxSize=10*1024*1024L;
    private List<String > allowedExtensions=List.of("pdf", "doc", "docx", "txt");
    /** 允许的真实 MIME 类型 */
    private List<String> allowedContentTypes = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream",
            "text/plain");
}
