package com.interviewmind.modules.interview.model.backendModel;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SkillDefinition {
    /** 目录名，加载时回填（YAML 里没有这个字段） */
    private String id;

    // ===== 来自 SKILL.md front matter =====
    private String name;
    private String description;

    // ===== 来自 skill.meta.yml =====
    private String displayName;
    private Display display;
    private List<Category> categories = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class Display {
        private String icon;
        private String gradient;
    }

    @Data
    @NoArgsConstructor
    public static class Category {
        private String key;
        private String label;
        private String priority;
        private String ref;
    }
}
