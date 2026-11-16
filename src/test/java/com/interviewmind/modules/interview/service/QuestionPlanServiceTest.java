package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import com.interviewmind.modules.interview.model.backendModel.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionPlanServiceTest {

    private final QuestionPlanService service = new QuestionPlanService();

    private static SkillDefinition.Category category(String key, String priority) {
        SkillDefinition.Category category = new SkillDefinition.Category();
        category.setKey(key);
        category.setLabel(key);
        category.setPriority(priority);
        return category;
    }

    /** 和 java-backend 的 skill.meta.yml 保持一致 */
    private static List<SkillDefinition.Category> javaBackend() {
        return List.of(
                category("JAVA", "CORE"),
                category("MYSQL", "CORE"),
                category("REDIS", "CORE"),
                category("SPRING", "NORMAL"),
                category("PROJECT", "ALWAYS_ONE"));
    }

    @Test
    void 十道题的配额应与设计一致() {
        List<CategoryQuota> plan = service.plan(10, javaBackend());

        assertThat(plan).extracting(CategoryQuota::categoryKey)
                .containsExactly("JAVA", "MYSQL", "REDIS", "SPRING", "PROJECT");
        assertThat(plan).extracting(CategoryQuota::quota)
                .containsExactly(3, 2, 2, 2, 1);
        assertThat(plan).extracting(CategoryQuota::quota).doesNotContain(0);
    }

    @Test
    void 各分类配额之和必须等于总题数() {
        for (int total = 1; total <= 20; total++) {
            List<CategoryQuota> plan = service.plan(total, javaBackend());
            int sum = plan.stream().mapToInt(CategoryQuota::quota).sum();
            assertThat(sum).as("total=%d 时配额之和", total).isEqualTo(total);
        }
    }

    @Test
    void 道题少时必须优先保留保底分类() {
        List<CategoryQuota> plan = service.plan(1, javaBackend());

        assertThat(plan).extracting(CategoryQuota::quota)
                .containsExactly(0, 0, 0, 0, 1);
    }

    @Test
    void 没有任何保底分类时按权重分配() {
        List<SkillDefinition.Category> categories = List.of(
                category("JAVA", "CORE"),
                category("SPRING", "NORMAL"));

        List<CategoryQuota> plan = service.plan(5, categories);

        // 权重 3:2 → 5 题按 3:2 分成 3 和 2
        assertThat(plan).extracting(CategoryQuota::quota).containsExactly(3, 2);
    }

    @Test
    void 非法题量应抛业务异常() {
        assertThatThrownBy(() -> service.plan(0, javaBackend()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    void 分类为空应抛业务异常() {
        assertThatThrownBy(() -> service.plan(5, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }
}