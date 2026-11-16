package com.interviewmind.modules.interview.model.backendModel;

/** 一个分类分到几道题。会被序列化成 plan_json 存库 */
public record CategoryQuota(
        String categoryKey,
        String label,
        String priority,
        int quota) {
}