package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.CategoryQuota;
import com.interviewmind.modules.interview.model.SkillDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionPlanService {
    private static final int WEIGHT_CORE = 3;
    private static final int WEIGHT_NORMAL = 2;
    private static final int WEIGHT_ALWAYS_ONE = 0;

    private static final String PRIORITY_CORE = "CORE";
    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String PRIORITY_ALWAYS_ONE = "ALWAYS_ONE";

    public List<CategoryQuota> plan(int totalQuestions, List<SkillDefinition.Category> categories) {
        if(totalQuestions <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,"问题总数必须大于0");
        }
        if(categories == null || categories.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,"该面试方向没有分类");
        }
        int size=categories.size();
        int[] quotas=new int[size];

        int guardCount=0;
        for(int index=0;index<size;index++) {
            if(PRIORITY_ALWAYS_ONE.equals(categories.get(index).getPriority())) {
                quotas[index]=1;
                guardCount++;
            }
        }
        if(guardCount>totalQuestions) {
            int kept=0;
            for(int index=0;index<size;index++) {
                if(quotas[index]==1) {
                    if(kept<totalQuestions) {
                        kept++;
                    }else{
                        quotas[index]=0;
                    }
                }
            }
            return build(categories,quotas);
        }
        int remain=totalQuestions-guardCount;
        if(remain==0) {
            return build(categories,quotas);
        }

        int[] weights=new int[size];
        int weightSum=0;
        for(int index=0;index<size;index++) {
            weights[index]=weightOf(categories.get(index).getPriority());
            weightSum+=weights[index];
        }
        if(weightSum==0) {
            quotas[0]+=remain;
            return build(categories,quotas);
        }

        int[] remainders=new int[size];
        int assigned=0;
        for(int index=0;index<size;index++) {
            int product=remain*weights[index];
            quotas[index]+=product/weightSum;
            remainders[index]=product%weightSum;
            assigned+=product/weightSum;
        }
        int leftover=remain-assigned;
        boolean[] used=new boolean[size];
        for(int seat=0;seat<leftover;seat++) {
            int best=-1;
            for(int index2=0;index2<size;index2++) {
                if(used[index2]||weights[index2]==0) {
                    continue;
                }
                if(best==-1||remainders[index2]>remainders[best]) {
                    best=index2;
                }
            }
            if(best==-1) {
                break;
            }
            quotas[best]++;
            used[best]=true;
        }
        return build(categories,quotas);
    }

    private List<CategoryQuota> build(List<SkillDefinition.Category> categories,int[] quotas) {
        List<CategoryQuota> result = new ArrayList<>(categories.size());
        for (int i = 0; i < categories.size(); i++) {
            SkillDefinition.Category category = categories.get(i);
            result.add(new CategoryQuota(
                    category.getKey(),
                    category.getLabel(),
                    normalizePriority(category.getPriority()),
                    quotas[i]));
        }
        return result;
    }

    private int weightOf(String priority){
        return switch (normalizePriority(priority)) {
            case PRIORITY_CORE -> WEIGHT_CORE;
            case PRIORITY_NORMAL -> WEIGHT_NORMAL;
            default -> WEIGHT_ALWAYS_ONE;
        };
    }

    private String normalizePriority(String priority){
        if(priority==null||priority.isBlank()){
            return PRIORITY_NORMAL;
        }
        return priority.trim().toUpperCase();
    }
}
