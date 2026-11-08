package com.interviewmind.modules.interview.controller;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.interview.model.AnswerRequest;
import com.interviewmind.modules.interview.model.CurrentQuestionResponse;
import com.interviewmind.modules.interview.model.SummaryQuestionResponse;
import com.interviewmind.modules.interview.service.InterviewAnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview/sessions/{id}")
@RequiredArgsConstructor
public class InterviewAnswerController {

    private final InterviewAnswerService interviewAnswerService;

    /** 提交一题的回答；questionIndex 是"第几题"（从 0 开始），用于顺序校验 */
    @PostMapping("/answers")
    public Result<CurrentQuestionResponse> answerQuestion(@PathVariable("id") Long id,
                                                          @RequestParam("questionIndex") Integer questionIndex,
                                                          @Valid @RequestBody AnswerRequest answer) {
        return Result.success(interviewAnswerService.answerQuestion(id, questionIndex, answer));
    }

    /** 查询当前该答哪一题 */
    @GetMapping("/current")
    public Result<CurrentQuestionResponse> getCurrentQuestion(@PathVariable("id") Long id) {
        return Result.success(interviewAnswerService.getCurrentQuestion(id));
    }

    /** 本场全部题目（含已答过的） */
    @GetMapping("/result")
    public Result<List<SummaryQuestionResponse>> getAllQuestions(@PathVariable("id") Long id) {
        return Result.success(interviewAnswerService.getAllQuestions(id));
    }
}
