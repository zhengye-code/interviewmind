package com.interviewmind.modules.resume.listener;

import com.interviewmind.common.constant.AsyncTaskStreamConstants;
import com.interviewmind.modules.resume.service.ResumeAnalyzeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAnalyzeProducer {
    private final StringRedisTemplate redisTemplate;
    public boolean enqueue(long resumeId,int retryCount){
        try {
            Map<String, String> message = Map
                    .of(AsyncTaskStreamConstants.FIELD_RESUME_ID, String.valueOf(resumeId),
                    AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount));
            RecordId recordId = redisTemplate.opsForStream()
                    .add(AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,message);
            log.info("Enqueue resume {} recordId {}",resumeId,recordId);
            return true;
        } catch (Exception e) {
            log.error("Enqueue resume {} error",resumeId,e);
            return false;
        }
    }
    public  boolean enqueue(long resumeId){
       return enqueue(resumeId,0);
    }
}
