package com.interviewmind.modules.ai;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {
    private final ChatClient chatClient;
    public String chat(String message) {
        try {
            String answer=chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            log.info("AI 调用成功，提问长度={}，回答长度={}", message.length(), answer==null?0:answer.length());
            return answer;
        }catch (Exception e){
            log.error("大模型调用失败:",e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,"AI服务暂时不可用，请稍后重试",e);
        }
    }
}
