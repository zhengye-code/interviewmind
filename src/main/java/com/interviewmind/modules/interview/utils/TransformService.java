package com.interviewmind.modules.interview.utils;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.CategoryQuota;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试模块的 JSON / 配额转换工具。
 * 只有静态方法，不注册成 Bean（不需要被注入）。
 */
@Slf4j
public final class TransformService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TransformService() {
    }

    /** 从配额单生成 分类key → 展示名 的映射 */
    public static Map<String, String> categoryLabels(List<CategoryQuota> plan) {
        Map<String, String> labels = new HashMap<>();
        for (CategoryQuota quota : plan) {
            labels.put(quota.categoryKey(), quota.label());
        }
        return labels;
    }

    /** plan_json → List<CategoryQuota>；解析失败返回空列表（降级，不抛异常） */
    public static List<CategoryQuota> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<CategoryQuota>>() {
            });
        } catch (JacksonException e) {
            log.warn("解析出题配额 JSON 失败,json={}", json);
            return List.of();
        }
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化失败", e);
        }
    }
}
