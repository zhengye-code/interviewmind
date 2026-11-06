package com.interviewmind.modules.interview.controller;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.interview.model.QuestionResponse;
import com.interviewmind.modules.interview.service.InterviewQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview/sessions")
@RequiredArgsConstructor
public class InterviewQuestionController {

    private final InterviewQuestionService questionService;

    /** 出题并落库（幂等：重复调用返回已有题目） */
    @PostMapping("/{id}/generate")
    public Result<List<QuestionResponse>> generate(@PathVariable("id") Long id) {
        return Result.success(questionService.generate(id));
    }

    /** 列出本场全部题目（不含答案要点） */
    @GetMapping("/{id}/questions")
    public Result<List<QuestionResponse>> questions(@PathVariable("id") Long id) {
        return Result.success(questionService.getQuestions(id));
    }
}