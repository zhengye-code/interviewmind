package com.interviewmind.modules.interview.controller;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.interview.model.CreateSessionRequest;
import com.interviewmind.modules.interview.model.InterviewEvaluationResponse;
import com.interviewmind.modules.interview.model.SessionResponse;
import com.interviewmind.modules.interview.repository.InterviewSessionRepository;
import com.interviewmind.modules.interview.service.InterviewEvaluateService;
import com.interviewmind.modules.interview.service.InterviewSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interview/sessions")
public class InterviewSessionController {
    private final InterviewSessionService sessionService;
    private final InterviewEvaluateService evaluationService;
    @PostMapping
    public Result<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return Result.success(sessionService.create(request));
    }

    @GetMapping("/{id}")
    public Result<SessionResponse> detail(@PathVariable("id") Long id) {
        return Result.success(sessionService.get(id));
    }

    @PostMapping("/{id}/evaluate")
    public Result<InterviewEvaluationResponse> evaluate(@PathVariable("id") Long id) {
        return Result.success(evaluationService.evaluate(id));
    }
}
