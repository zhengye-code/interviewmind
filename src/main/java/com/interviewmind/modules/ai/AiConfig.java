package com.interviewmind.modules.ai;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    private static final String SYSTEM_PROMPT ="""
            你是一位资深的技术面试官，擅长 Java 后端、Spring 生态与系统设计。
            回答要准确、简洁，用中文作答，控制在 200 字以内。
            """;
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
