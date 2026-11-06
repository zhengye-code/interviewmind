package com.interviewmind.modules.interview.controller;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.interview.service.InterviewSkillService;
import com.interviewmind.modules.interview.model.SkillDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview/skills")
@RequiredArgsConstructor
public class InterviewSkillController {
    private final InterviewSkillService interviewSkillService;
    @GetMapping
    public Result<List<SkillDefinition>> list() {
        return Result.success(interviewSkillService.getSkillDefinitions());
    }

    @GetMapping("{id}")
    public Result<SkillDefinition> getSkill(@PathVariable("id") String id) {
        return Result.success(interviewSkillService.getSkillDefinition(id));
    }
}
