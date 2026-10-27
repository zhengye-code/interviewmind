package com.interviewmind.modules.ai;

import com.interviewmind.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiChatService aiChatService;
    @GetMapping("ping")
    public Result<String> ping(@RequestParam("message") String message){
        return Result.success(aiChatService.chat(message));
    }
}
