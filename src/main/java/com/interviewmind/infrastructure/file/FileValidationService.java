package com.interviewmind.infrastructure.file;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@Component
public class FileValidationService {

    /**
     * 扩展名 -> 该扩展名「应该」对应的真实 MIME 类型。
     * 用来识别改名伪装：把 .txt 改成 .pdf 时，扩展名是 pdf 但真实内容是 text/plain，两者不符。
     */
    private static final Map<String, String> EXPECTED_TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain"
    );

    public void validateFile(MultipartFile file, long maxSizeBytes,
                             Collection<String> allowedExtensions, String businessName) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_FILE_EMPTY,
                    "请选择要上传的" + businessName + "文件");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.RESUME_FILE_TOO_LARGE,
                    businessName + "大小不能超过 " + (maxSizeBytes / 1024 / 1024) + "MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.RESUME_FILE_TYPE_NOT_SUPPORTED,
                    businessName + "只支持 " + String.join(" / ", allowedExtensions) + " 格式");
        }
    }

    /** 取小写扩展名（不含点）；没有扩展名时返回空字符串 */
    public String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index == -1 || fileName.length() == index + 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /** 校验 Tika 探测出的真实 MIME 类型是否在白名单内 */
    public void validateContentType(String detectedContentType, Collection<String> allowedTypes,
                                    String businessName) {
        if (detectedContentType == null || !allowedTypes.contains(detectedContentType)) {
            throw new BusinessException(ErrorCode.RESUME_FILE_TYPE_NOT_SUPPORTED,
                    businessName + "文件类型不支持: " + detectedContentType);
        }
    }

    /**
     * 校验「扩展名」与「真实内容类型」是否自洽，用于识别改名伪装。
     * 未知扩展名交给前置的 validateFile 拦截，这里只做已知扩展名的比对。
     */
    public void validateExtensionMatchesContent(String extension, String detectedContentType,
                                                String businessName) {
        String expected = EXPECTED_TYPE_BY_EXTENSION.get(extension);
        if (expected != null && !expected.equals(detectedContentType)) {
            throw new BusinessException(ErrorCode.RESUME_FILE_TYPE_NOT_SUPPORTED,
                    businessName + "文件内容与扩展名不符（扩展名 ." + extension
                            + "，实际内容类型 " + detectedContentType + "）");
        }
    }
}
