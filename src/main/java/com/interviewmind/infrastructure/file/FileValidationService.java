package com.interviewmind.infrastructure.file;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

@Component
public class FileValidationService {
    public void validateFile(MultipartFile file, long maxSizeBytes,
                             Collection<String > allowedExtensions,String businessName){
        if(file==null||file.isEmpty()){
            throw new BusinessException(ErrorCode.RESUME_FILE_EMPTY,"请选择要上传的" + businessName + "文件");
        }
        if(file.getSize()>maxSizeBytes){
            throw new BusinessException(ErrorCode.RESUME_FILE_TOO_LARGE,
                    businessName + "大小不能超过 " + (maxSizeBytes / 1024 / 1024) + "MB");
        }
        String extension=extentionOf(file.getOriginalFilename());
        if(!allowedExtensions.contains(extension)){
            throw new BusinessException(ErrorCode.RESUME_FILE_TYPE_NOT_SUPPORTED,
                    businessName + "只支持 " + String.join(" / ", allowedExtensions) + " 格式");
        }
    }
    public String extentionOf(String fileName){
        if(fileName==null||fileName.isEmpty()){
            return "";
        }
        int index = fileName.lastIndexOf(".");
        if(index==-1||fileName.length()==index+1){
            return "";
        }
        return fileName.substring(index+1).toLowerCase(Locale.ROOT);
    }
}
