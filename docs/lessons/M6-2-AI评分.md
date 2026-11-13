# M6-2：让 AI 一次给全部题目打分

> **本步目标**：实现"评分"这件事本身。
>
> ```
> POST /api/interview/sessions/{id}/evaluate
> ```
>
> 调一次 AI，拿回：每题几分 + 命中哪些要点 + 漏了什么 + 评语 + 整场总结。
> 然后存进 M6-1 建好的两张表，并保证重复调用不重复花钱。
>
> **本步完成后，你的项目就有了"反馈"** —— 这是整个模拟面试最核心的价值。

---

# 第 0 部分：整体形状

```
① 读会话            → 状态必须是 COMPLETED（答完了才能评）
② 幂等检查          → 已有报告？直接返回，不调 AI
③ 读题目和回答      → 10 道题，每道都有 answer_text
④ 拼成 10 个"评分材料"（题 / 参考要点 / 用户回答）
⑤ 拼成一大段文字，一次问 AI
⑥ AI 返回：每题分数 + 命中 + 遗漏 + 评语 + 总结
⑦ 代码校验并修正    → 分数越界钳制、漏评的题目记日志
⑧ 代码算总分        → 平均分 × 10
⑨ 事务写库          → 10 行逐题分 + 1 行报告
⑩ 返回给前端
```

---

# 第 1 部分：三个关键设计

## 1.1 为什么是一次调用，不是十次

| | 10 次调用 | 1 次调用 |
|---|---|---|
| 耗时 | 60–100 秒 | 20–40 秒 |
| 超时风险 | 10 次 | 1 次 |
| 计费 | 10 次请求 | 1 次请求 |
| 全局视角 | ❌ 每题孤立 | ✅ 能说"三次都答得太浅" |

**关键是最后一行**：一次调用时，模型能同时看到 10 个回答，才能给出"整体表达偏啰嗦""基础扎实但缺深度"这类跨题结论。分 10 次调用，它永远不知道其他题答得怎么样。

**代价**：prompt 会很长。所以第 1.3 节要限制每段回答的长度。

## 1.2 最重要的一个技巧：让模型「先写证据，再给分数」

### 现象

让大模型打分，它天然偏宽松。你说"严格一点"，它嘴上答应，分数照旧偏高。

### 原因

模型是**从左到右逐个 token 生成**的。如果你的输出格式是：

```
{ "score": 8, "hitPoints": [...] }
```

它先把 `8` 吐出来，然后才去想理由 —— 后面的 `hitPoints` 实际上是**为了圆那个 8 分编出来的**。

### 解法

**把字段顺序反过来。** 让模型先写"命中了哪几条、漏了哪几条"，最后才写分数：

```java
public record QuestionScore(
        int questionIndex,
        List<String> hitPoints,      // ① 先写证据
        List<String> missedPoints,   // ② 再写缺失
        int score,                   // ③ 最后给分
        String comment) {
}
```

**为什么改字段顺序就够了？** 因为 `.entity()` 会把 record 的字段顺序生成成 JSON Schema 的属性顺序，而模型是照着 Schema 从左到右生成的。

**字段顺序 = 模型的思考顺序。** 这是本步最值钱的一行知识。

再配一条提示词约束："如果给 8 分及以上，必须能在 hitPoints 里找到对应证据。"

## 1.3 数值计算归代码，文字判断归 AI

```
AI 负责：每题分数、命中/遗漏、评语、总结、优势、建议
代码负责：总分 = 平均分 × 10、分数是否越界、题目数量是否对得上
```

**为什么总分不让 AI 给**：如果每题分是 AI 给的、总分也是 AI 给的，两者可能对不上（平均 6.0 但总分写 72），你根本不知道哪个错。

**为什么不让代码写评语**：代码写不出"你对扩容机制的理解是对的，但没讲清楚为什么必须是 2 的幂"这种话。

各干各擅长的。

## 1.4 多长算太长：给每段回答设上限

10 道题，如果每人都写 5000 字（`AnswerRequest` 的上限），prompt 就是 5 万字，光输入就 3 万多 token，又贵又慢，还容易让模型"看花眼"。

所以拼接时**每段回答截断到 1500 字符**：

```
MAX_ANSWER_CHARS = 1500
```

为什么是 1500：一个认真作答的技术回答，1500 字已经足够体现水平；超出的部分通常是复述题面或凑字数。

> 这和 M4-3 里"简历正文截断 1500 字符"是同一个思路：**给 AI 的材料要够用，但不要无限**。

---

# 第 2 部分：两个提示词

## 2.1 `src/main/resources/prompts/interview-evaluation-system.st`

```
你是一位有 10 年经验的资深技术面试官，负责评估候选人对面试题的回答质量。

评估原则：
1. 只依据候选人实际说出的内容判断，不推测他"可能知道"；
2. 技术细节错误要明确指出，不要含糊带过；
3. 区分"背概念"和"讲清楚原理与取舍"，后者才值得高分；
4. 语气专业、直接，不写客套话。
```

**为什么系统提示词这么短**：它只放"这个人是谁、他怎么看问题"。具体的评分规则放在用户提示词里，因为规则会随场景变（比如以后加了 RAG，规则会改成"以检索到的标准答案为准"）。

## 2.2 `src/main/resources/prompts/interview-evaluation-user.st`

```
下面是候选人对 {totalQuestions} 道面试题的回答，请逐题打分。

{questionBlocks}

打分要求：
1. 以每题的「参考要点」为尺子：候选人提到了哪几条，就写进 hitPoints；没提到的，写进 missedPoints；
2. hitPoints 和 missedPoints 必须具体，能对应到候选人回答里的原话或要点；不要写"回答基本正确"这类空话；
3. 不要替候选人补全答案。他没说出来的推论，不算命中；
4. 每题的 score 是 0 到 10 的整数，标准如下：
   - 0 到 2：答错、答非所问或基本没答；
   - 3 到 4：概念有明显错误；
   - 5 到 6：基本正确，但缺少深度或细节；
   - 7 到 8：正确且有条理，能讲到关键细节；
   - 9 到 10：深入，且能讲清取舍、边界或实际经验。
5. 不要给鼓励分。如果给 8 分及以上，必须能在 hitPoints 里找到对应证据；
6. comment 只写一句话，指出最关键的问题或亮点；
7. 最后再给整体 summary（3 到 5 句）、strengths（1 到 3 条）、suggestions（2 到 4 条，要具体可执行）。
```

### 三个值得注意的写作技巧

**① 给"评分标准"而不是"要严格"**

"严格一点"是形容词，模型没法执行。给出 0-10 每一档的具体含义，它才能对齐。

**这和 M4-3 里把 `HARD` 翻译成"聚焦边界条件、故障排查与设计取舍"是同一个技巧：不要给形容词，给判断依据。**

**② "不要替候选人补全答案"**

这句是防一个很常见的失败模式：候选人答得模糊，模型自动脑补成"他大概知道"。

**③ 明确禁止空话**

"不要写『回答基本正确』这类空话" —— 如果不写这句，`hitPoints` 里大概率会出现一堆"理解较深入""表达清晰"这种没有信息量的内容。

---

# 第 3 部分：三个模型类

## 3.1 `EvaluationItem` —— 喂给 AI 的"薄"材料

`src/main/java/com/interviewmind/modules/interview/model/EvaluationItem.java`

```java
package com.interviewmind.modules.interview.model;

import java.util.List;

/**
 * 一道题的评分材料。
 *
 * 为什么不用 InterviewQuestionEntity 直接传？
 *   因为"AI 层"不该认识数据库实体。给它一个只含 5 个字段的小对象，
 *   以后实体加列（比如加 score_version）时，AI 层完全不受影响。
 */
public record EvaluationItem(
        int questionIndex,
        String categoryKey,
        String questionText,
        List<String> expectedPoints,
        String answerText) {
}
```

**为什么叫 `Item` 不叫 `Request`**：它不是前端发来的，也不是返回给前端的，是**程序内部传参用的**。名字要说明它的角色。

## 3.2 `InterviewEvaluationResult` —— AI 返回的结构

`src/main/java/com/interviewmind/modules/interview/model/InterviewEvaluationResult.java`

```java
package com.interviewmind.modules.interview.model;

import java.util.List;

/**
 * AI 返回的评分结果。
 *
 * ⚠️ 字段顺序是有意义的：模型按 Schema 的属性顺序生成，
 *    所以"证据在前、结论在后"能显著减少模型乱给高分。
 */
public record InterviewEvaluationResult(
        List<QuestionScore> questionScores,
        String summary,
        List<String> strengths,
        List<String> suggestions) {

    public record QuestionScore(
            int questionIndex,          // ① 先定位是哪一题
            List<String> hitPoints,     // ② 再写命中的证据
            List<String> missedPoints,  // ③ 再写遗漏
            int score,                  // ④ 最后才给分数
            String comment) {
    }
}
```

### 为什么 `questionIndex` 是第一个字段

因为它是**对齐用的键**。模型要给 10 道题打分，你必须有办法知道哪条对应哪道题。

用 `questionIndex` 而不是题目文字来对齐，是因为文字可能被模型改写（它有时会"意译"），而下标是数字，不会变。

**这也是为什么第 4.3 节要对齐做校验**：万一模型漏了一题，靠下标就能发现。

## 3.3 `EvaluationResponse` —— 返回给前端的

`src/main/java/com/interviewmind/modules/interview/model/EvaluationResponse.java`

```java
package com.interviewmind.modules.interview.model;

import java.time.LocalDateTime;
import java.util.List;

/** 评分结果（返回给前端） */
public record EvaluationResponse(
        Long sessionId,
        int overallScore,
        int scoredQuestions,
        String summary,
        List<String> strengths,
        List<String> suggestions,
        String model,
        LocalDateTime evaluatedAt,
        List<QuestionScoreDetail> questionScores) {

    public record QuestionScoreDetail(
            int questionIndex,
            int score,
            List<String> hitPoints,
            List<String> missedPoints,
            String comment) {
    }
}
```

**注意这里字段顺序和 AI 那个不同**：`score` 放在了 `hitPoints` 前面。

为什么可以这样？因为**这个类不参与 AI 生成**，它只是返回给前端的。前端怎么方便怎么排，不需要考虑"模型的思考顺序"。

> **同一个概念，在"给 AI 的类型"和"给前端的类型"里可以有不同的字段顺序。** 它们本来就不是一个东西。

---

# 第 4 部分：写代码

## 4.1 先加一个错误码

`ErrorCode.java` 的面试模块，3005 后面追加：

```java
    INTERVIEW_EVALUATION_FAILED(3006, "面试评分失败"),
```

> 3007 你已经在用了（答题顺序不匹配），所以 3006 正好空着，放在 3005 后面顺序也顺。

## 4.2 AI 调用层：`InterviewGradingService`

`src/main/java/com/interviewmind/modules/interview/service/InterviewGradingService.java`

```java
package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.EvaluationItem;
import com.interviewmind.modules.interview.model.InterviewEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 只负责"问 AI"，不碰数据库。
 * 命名和 resume 模块的 ResumeGradingService 保持一致：
 *   *GradingService = 打分这件事的 AI 层。
 */
@Slf4j
@Service
public class InterviewGradingService {

    /** 每道题的回答最多喂给模型这么多字，控制 token 成本 */
    private static final int MAX_ANSWER_CHARS = 1500;

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final PromptTemplate userPromptTemplate;

    public InterviewGradingService(ChatClient chatClient, ResourceLoader resourceLoader) throws IOException {
        this.chatClient = chatClient;
        this.systemPrompt = load(resourceLoader, "classpath:prompts/interview-evaluation-system.st");
        this.userPromptTemplate = new PromptTemplate(
                load(resourceLoader, "classpath:prompts/interview-evaluation-user.st"));
    }

    private static String load(ResourceLoader resourceLoader, String location) throws IOException {
        return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
    }

    /** 一次调用，给全部题目打分 */
    public InterviewEvaluationResult evaluate(List<EvaluationItem> items) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("totalQuestions", items.size());
        variables.put("questionBlocks", buildBlocks(items));

        String userText = userPromptTemplate.render(variables);

        try {
            InterviewEvaluationResult result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userText)
                    .call()
                    .entity(InterviewEvaluationResult.class);

            if (result == null || result.questionScores() == null || result.questionScores().isEmpty()) {
                throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "AI 没有返回任何评分");
            }
            log.info("AI 评分完成: 提交 {} 题, 返回 {} 题", items.size(), result.questionScores().size());
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 评分失败", e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "AI 评分失败：" + e.getMessage(), e);
        }
    }

    /** 把 10 道题拼成一段给模型看的文字 */
    private String buildBlocks(List<EvaluationItem> items) {
        StringBuilder sb = new StringBuilder();
        for (EvaluationItem item : items) {
            sb.append("--- 第 ").append(item.questionIndex() + 1).append(" 题 ---\n");
            sb.append("分类：").append(item.categoryKey()).append('\n');
            sb.append("题目：").append(item.questionText()).append('\n');
            sb.append("参考要点：\n");
            if (item.expectedPoints() == null || item.expectedPoints().isEmpty()) {
                sb.append("- （无参考要点，按你的专业判断评分）\n");
            } else {
                item.expectedPoints().forEach(point -> sb.append("- ").append(point).append('\n'));
            }
            sb.append("候选人的回答：\n")
              .append(truncate(item.answerText(), MAX_ANSWER_CHARS))
              .append("\n\n");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "（未作答）";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "……（回答过长，已截断）";
    }
}
```

### 几个说明

**① 为什么拼接用 `StringBuilder` 而不是 `String +`**

因为要拼 10 段。`String` 是不可变的，每次 `+` 都会新建一个字符串对象，10 段拼接会产生 10 个中间对象。`StringBuilder` 是同一块缓冲区里追加。

数据量小的时候差别看不见，但这是"**循环里不要用 `+` 拼字符串**"的基本习惯。

**② 为什么 `{questionBlocks}` 里可以放任意内容（包括 `{` `}`）**

因为变量是模板的**值**，不是模板的一部分。StringTemplate 渲染时把值原样插进去，**不再二次解析**。

所以候选人的回答里即使有代码 `{ ... }`，也完全没问题。这个规律在 M4-3 你已经验证过了（当时是简历文本）。

**③ 为什么异常处理分两段**

```java
catch (BusinessException e) { throw e; }        // 自己抛的，原样往上送
catch (Exception e) { ... 包成 BusinessException ... }   // 框架抛的，转成业务错误
```

如果只写一个 `catch (Exception)`，会把我们精心抛出的 `BusinessException(3006)` 再包一层，错误码就丢了。

## 4.3 事务层：`InterviewEvaluationPersister`

`src/main/java/com/interviewmind/modules/interview/service/InterviewEvaluationPersister.java`

```java
package com.interviewmind.modules.interview.service;

import com.interviewmind.modules.interview.model.InterviewQuestionScoreEntity;
import com.interviewmind.modules.interview.model.InterviewReportEntity;
import com.interviewmind.modules.interview.repository.InterviewQuestionScoreRepository;
import com.interviewmind.modules.interview.repository.InterviewReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 只做一件事：在一个事务里写 10 行逐题分 + 1 行报告。
 *
 * 为什么单独一个类：和 M5-1 的 TransactionalService 同样的理由 ——
 *   @Transactional 靠代理生效，同类自调用无效；
 *   而调用方要在事务外面 catch 唯一约束冲突。
 */
@Component
@RequiredArgsConstructor
public class InterviewEvaluationPersister {

    private final InterviewQuestionScoreRepository scoreRepo;
    private final InterviewReportRepository reportRepo;

    @Transactional
    public InterviewReportEntity save(List<InterviewQuestionScoreEntity> scores, InterviewReportEntity report) {
        scoreRepo.saveAll(scores);
        return reportRepo.save(report);
    }
}
```

**为什么是"一起存"而不是"先存分数再存报告"**：

如果先存了 10 行逐题分，存报告时失败了，数据库里就留下 10 行没有报告的评分。下次调用时：

- 幂等检查看的是"报告存不存在" → 判断为"没评过" → 又调一次 AI
- 再存 10 行 → 撞上 `uk_iqs_question` 唯一约束 → 报错

**所以要么全成功，要么全失败。这就是事务存在的意义。**

## 4.4 编排层：`InterviewEvaluationService`

`src/main/java/com/interviewmind/modules/interview/service/InterviewEvaluationService.java`

```java
package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.EvaluationItem;
import com.interviewmind.modules.interview.model.EvaluationResponse;
import com.interviewmind.modules.interview.model.InterviewEvaluationResult;
import com.interviewmind.modules.interview.model.InterviewQuestionEntity;
import com.interviewmind.modules.interview.model.InterviewQuestionScoreEntity;
import com.interviewmind.modules.interview.model.InterviewReportEntity;
import com.interviewmind.modules.interview.model.InterviewSessionEntity;
import com.interviewmind.modules.interview.model.InterviewSessionStatus;
import com.interviewmind.modules.interview.repository.InterviewQuestionRepository;
import com.interviewmind.modules.interview.repository.InterviewQuestionScoreRepository;
import com.interviewmind.modules.interview.repository.InterviewReportRepository;
import com.interviewmind.modules.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {

    private final InterviewSessionRepository sessionRepo;
    private final InterviewQuestionRepository questionRepo;
    private final InterviewQuestionScoreRepository scoreRepo;
    private final InterviewReportRepository reportRepo;
    private final InterviewGradingService gradingService;
    private final InterviewEvaluationPersister persister;
    private final ObjectMapper objectMapper;

    /** 记录用哪个模型评的，便于以后换模型时区分历史数据 */
    @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}")
    private String modelName;

    /**
     * 评分（幂等）。
     *
     * 这个方法故意不加 @Transactional：
     *   里面有一次 20-40 秒的 AI 调用，包在事务里会一直占着数据库连接。
     *   真正需要事务的只有最后那次写入，交给 InterviewEvaluationPersister。
     */
    public EvaluationResponse evaluate(Long sessionId) {
        InterviewSessionEntity session = loadSession(sessionId);

        // ① 幂等：已经有报告就直接返回
        EvaluationResponse existing = loadExisting(sessionId);
        if (existing != null) {
            log.info("已存在评分结果，直接返回: sessionId={}", sessionId);
            return existing;
        }

        // ② 必须答完才能评
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_STATE_INVALID,
                    "面试尚未结束，无法评分（当前进度 "
                            + session.getCurrentQuestionIndex() + "/" + session.getTotalQuestions() + "）");
        }

        // ③ 读出题目
        List<InterviewQuestionEntity> questions =
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "本场面试没有题目");
        }

        long unanswered = questions.stream().filter(q -> q.getAnsweredAt() == null).count();
        if (unanswered > 0) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_STATE_INVALID,
                    "还有 " + unanswered + " 道题未作答，无法评分");
        }

        // ④ 组装评分材料
        List<EvaluationItem> items = questions.stream()
                .map(q -> new EvaluationItem(
                        q.getQuestionIndex(),
                        q.getCategoryKey(),
                        q.getQuestionText(),
                        parsePoints(q.getExpectedPoints()),
                        q.getAnswerText()))
                .toList();

        // ⑤ 一次调用，全部打分
        log.info("开始评分: sessionId={}, 题数={}", sessionId, items.size());
        InterviewEvaluationResult aiResult = gradingService.evaluate(items);

        // ⑥ 对齐：按 questionIndex 找到每题对应的评分
        Map<Integer, InterviewEvaluationResult.QuestionScore> scoreByIndex = new HashMap<>();
        for (InterviewEvaluationResult.QuestionScore score : aiResult.questionScores()) {
            if (score != null) {
                scoreByIndex.put(score.questionIndex(), score);
            }
        }

        Map<Integer, Long> questionIdByIndex = new HashMap<>();
        for (InterviewQuestionEntity question : questions) {
            questionIdByIndex.put(question.getQuestionIndex(), question.getId());
        }

        // ⑦ 逐题校验
        List<InterviewQuestionScoreEntity> scoreEntities = new ArrayList<>();
        for (InterviewQuestionEntity question : questions) {
            Integer index = question.getQuestionIndex();
            InterviewEvaluationResult.QuestionScore score = scoreByIndex.get(index);
            if (score == null) {
                log.warn("AI 漏评第 {} 题，跳过: sessionId={}", index, sessionId);
                continue;
            }
            int clamped = clamp(score.score(), 0, 10);
            if (clamped != score.score()) {
                log.warn("AI 给出越界分数 {}，已修正为 {}: sessionId={}, 题号={}",
                        score.score(), clamped, sessionId, index);
            }

            InterviewQuestionScoreEntity entity = new InterviewQuestionScoreEntity();
            entity.setSessionId(sessionId);
            entity.setQuestionId(questionIdByIndex.get(index));
            entity.setScore(clamped);
            entity.setHitPointsJson(toJson(score.hitPoints()));
            entity.setMissedPointsJson(toJson(score.missedPoints()));
            entity.setComment(score.comment());
            scoreEntities.add(entity);
        }

        if (scoreEntities.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "AI 没有给出任何有效评分，请重试");
        }

        // ⑧ 总分由代码算，不是 AI 给的
        int overallScore = (int) Math.round(
                scoreEntities.stream().mapToInt(InterviewQuestionScoreEntity::getScore).average().orElse(0) * 10);

        // ⑨ 报告
        InterviewReportEntity report = new InterviewReportEntity();
        report.setSessionId(sessionId);
        report.setOverallScore(overallScore);
        report.setSummary(aiResult.summary());
        report.setStrengthsJson(toJson(aiResult.strengths()));
        report.setSuggestionsJson(toJson(aiResult.suggestions()));
        report.setModel(modelName);

        try {
            InterviewReportEntity saved = persister.save(scoreEntities, report);
            log.info("评分完成: sessionId={}, 总分={}, 评了 {} 题", sessionId, overallScore, scoreEntities.size());
            return toResponse(saved, scoreEntities);
        } catch (DataIntegrityViolationException e) {
            // 并发评分：另一个请求抢先写进去了
            log.warn("并发评分冲突，返回已有结果: sessionId={}", sessionId);
            EvaluationResponse raced = loadExisting(sessionId);
            if (raced == null) {
                throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                        "评分写入冲突，请稍后重试", e);
            }
            return raced;
        }
    }

    // ==================== 私有方法 ====================

    private InterviewSessionEntity loadSession(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "面试会话不存在,id=" + sessionId));
    }

    /** 已评过就返回已有结果，没评过返回 null */
    private EvaluationResponse loadExisting(Long sessionId) {
        InterviewReportEntity report = reportRepo.findBySessionId(sessionId).orElse(null);
        if (report == null) {
            return null;
        }
        return toResponse(report, scoreRepo.findBySessionId(sessionId));
    }

    private EvaluationResponse toResponse(InterviewReportEntity report,
                                          List<InterviewQuestionScoreEntity> scores) {
        Map<Long, Integer> indexById = new HashMap<>();
        for (InterviewQuestionEntity question :
                questionRepo.findBySessionIdOrderByQuestionIndexAsc(report.getSessionId())) {
            indexById.put(question.getId(), question.getQuestionIndex());
        }

        List<EvaluationResponse.QuestionScoreDetail> details = scores.stream()
                .map(entity -> new EvaluationResponse.QuestionScoreDetail(
                        indexById.getOrDefault(entity.getQuestionId(), -1),
                        entity.getScore() == null ? 0 : entity.getScore(),
                        parsePoints(entity.getHitPointsJson()),
                        parsePoints(entity.getMissedPointsJson()),
                        entity.getComment()))
                .sorted(Comparator.comparingInt(EvaluationResponse.QuestionScoreDetail::questionIndex))
                .toList();

        return new EvaluationResponse(
                report.getSessionId(),
                report.getOverallScore() == null ? 0 : report.getOverallScore(),
                details.size(),
                report.getSummary(),
                parsePoints(report.getStrengthsJson()),
                parsePoints(report.getSuggestionsJson()),
                report.getModel(),
                report.getEvaluatedAt(),
                details);
    }

    /** 把一个 JSON 数组字符串读成 List&lt;String&gt;，解析失败就返回空列表 */
    private List<String> parsePoints(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            log.warn("解析 JSON 数组失败: {}", json);
            return List.of();
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "序列化失败", e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
```

### 逐块解释

**① 为什么要 `clamp`（钳制）**

提示词说"0 到 10"，但模型可能给 11、-1、或者 8.5（转 int 时会被截断）。

数据库有 `CHECK (score BETWEEN 0 AND 10)`，越界会**直接报错导致整次评分失败**。

所以我们宁可**修正**（8.5→8，11→10）并记一条 warn，也不要让一次 30 秒的 AI 调用因为一个数字白费。

> 兜底策略的选择：**能修正的就修正，不能修正的才报错。** 分数越界可以修正；题目一道都没有就只能报错。

**② 为什么"漏评"只是跳过 + 记日志**

AI 可能只返回 9 条。这时：

- 报错 → 用户白等 30 秒，还得重来
- 用默认分（0 分）补 → 会冤枉用户
- **跳过 + 记日志** → 9 题有分，报告里 `scoredQuestions = 9`，用户可以自己看到

第三种最诚实。

**③ 为什么平均分算出来后再乘 10**

```
逐题：3, 5, 6, 4    平均 = 4.5
总分 = 4.5 × 10 = 45
```

逐题是 0-10，总分是 0-100，所以乘 10。`Math.round` 把 45.5 变成 46（四舍五入），避免总分出现小数。

**④ 为什么报告表存 `model`**

将来你换模型（或者加了 RAG 重评）时，历史数据里能看出"这条 45 分是谁给的"。

这个值从配置里读：`@Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}")`。

`@Value` 的作用是"把配置文件里的值注入到这个字段"。冒号后面是**默认值**，配置里没写就用它。

**⑤ 为什么 `toResponse` 要再查一次题目表**

因为 `interview_question_scores` 表里只有 `question_id`，**没有 `question_index`**。

而返回给前端时要"按题号排序"，所以需要 question_id → question_index 的映射。

> 这是 M6-1 那个"要不要把 `question_index` 也冗余存下来"的取舍。当前选择是"不存"：省一列，多一次查询。这个查询走主键索引，很便宜，所以不存更划算。
>
> 如果将来发现这个查询成了瓶颈，再加一列也不迟。

## 4.5 接口

`src/main/java/com/interviewmind/modules/interview/controller/InterviewEvaluationController.java`

```java
package com.interviewmind.modules.interview.controller;

import com.interviewmind.common.result.Result;
import com.interviewmind.modules.interview.model.EvaluationResponse;
import com.interviewmind.modules.interview.service.InterviewEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview/sessions")
@RequiredArgsConstructor
public class InterviewEvaluationController {

    private final InterviewEvaluationService evaluationService;

    /** 给整场面试评分（幂等：评过就直接返回，不重复调 AI） */
    @PostMapping("/{id}/evaluate")
    public Result<EvaluationResponse> evaluate(@PathVariable("id") Long id) {
        return Result.success(evaluationService.evaluate(id));
    }
}
```

**为什么用 `POST` 而不是 `GET`**：

语义上它"不是只读的"——第一次调用会**创建**评分记录、**花钱**调 AI。

`GET` 应该是幂等且无副作用的（浏览器、爬虫、预加载都可能自动发 GET）。**如果评分是 GET，一个浏览器预取就可能让你白花一次 AI 的钱。**

---

# 第 5 部分：验收

## 5.1 编译 + 启动

```powershell
.\mvnw.cmd -q compile
.\scripts\run-dev.ps1
```

## 5.2 准备一场"答完的"面试

```powershell
# 1) 建会话（3 题，快一点）
curl.exe -s -X POST http://localhost:8080/api/interview/sessions -H "Content-Type: application/json" -d "{\"skillId\":\"java-backend\",\"totalQuestions\":3}"

# 2) 出题
curl.exe -s -X POST http://localhost:8080/api/interview/sessions/1/generate

# 3) 看当前题，拿到 questionIndex
curl.exe -s http://localhost:8080/api/interview/sessions/1/current

# 4) 逐题作答（注意 questionIndex 从 0 开始）
curl.exe -s -X POST "http://localhost:8080/api/interview/sessions/1/answers?questionIndex=0" -H "Content-Type: application/json" -d "{\"answerText\":\"HashMap 扩容是 2 倍扩容，因为容量保持 2 的幂，这样 index 计算可以用 hash & (n-1)，比取模快，而且扩容后元素要么在原位置、要么在 原位置+旧容量，迁移只需要一次判断。\"}"

curl.exe -s -X POST "http://localhost:8080/api/interview/sessions/1/answers?questionIndex=1" -H "Content-Type: application/json" -d "{\"answerText\":\"B+ 树非叶子节点不存数据，扇出更大，树更矮，磁盘 IO 更少；而且叶子节点之间有链表，范围查询很快。\"}"

curl.exe -s -X POST "http://localhost:8080/api/interview/sessions/1/answers?questionIndex=2" -H "Content-Type: application/json" -d "{\"answerText\":\"缓存穿透可以用布隆过滤器，或者把空结果也缓存起来。\"}"
```

确认状态已变成 `COMPLETED`：

```powershell
curl.exe -s http://localhost:8080/api/interview/sessions/1/current
```

## 5.3 评分（等 20–40 秒）

```powershell
curl.exe -s -X POST http://localhost:8080/api/interview/sessions/1/evaluate
```

期望看到：

```json
{"code":200,"data":{
  "sessionId":1,
  "overallScore":65,
  "scoredQuestions":3,
  "summary":"……",
  "strengths":["…"],
  "suggestions":["…"],
  "model":"deepseek-chat",
  "evaluatedAt":"…",
  "questionScores":[
    {"questionIndex":0,"score":7,"hitPoints":["…"],"missedPoints":["…"],"comment":"…"},
    ...
  ]}}
```

**重点检查三件事**：

| 检查 | 说明 |
|---|---|
| `questionScores` 有 3 条 | 数量对得上 |
| 每条都有 `hitPoints` 和 `missedPoints` | 说明"先证据后分数"那个技巧生效了 |
| `overallScore` ≈ 三条分数的平均 × 10 | 说明总分是代码算的，不是 AI 编的 |

## 5.4 再调一次（验证幂等，应该秒回）

```powershell
curl.exe -s -X POST http://localhost:8080/api/interview/sessions/1/evaluate
```

日志里应出现：

```
已存在评分结果，直接返回: sessionId=1
```

## 5.5 直接看数据库

```powershell
docker exec interviewmind-postgres psql -U postgres -d interview_guide -c "SELECT question_id, score, left(coalesce(comment,''),40) AS 评语 FROM interview_question_scores WHERE session_id=1 ORDER BY question_id;"

docker exec interviewmind-postgres psql -U postgres -d interview_guide -c "SELECT session_id, overall_score, model, left(coalesce(summary,''),50) AS 总结 FROM interview_reports;"
```

要看到：3 行逐题分（分数在 0–10）+ 1 行报告（总分 0–100）。

## 5.6 边界检查

**① 没答完就评分**

```powershell
# 新建一场，只出题不答题
curl.exe -s -X POST http://localhost:8080/api/interview/sessions -H "Content-Type: application/json" -d "{\"skillId\":\"java-backend\",\"totalQuestions\":3}"
curl.exe -s -X POST http://localhost:8080/api/interview/sessions/2/generate
curl.exe -s -X POST http://localhost:8080/api/interview/sessions/2/evaluate
```

期望：`code=3004`，`面试尚未结束，无法评分（当前进度 0/3）`。

**② 看看提示词实际发出去的样子（可选但很有用）**

在 `InterviewGradingService.buildBlocks` 里临时加一行日志：

```java
log.debug("评分提示词:\n{}", userText);
```

然后在 `application.properties` 里把日志级别调到 DEBUG：

```properties
logging.level.com.interviewmind.modules.interview.service.InterviewGradingService=DEBUG
```

重启后评一次，你就能看到**模型到底收到了什么**。看完记得删掉日志或调回 INFO。

> 排查 AI 问题时，**第一件事永远是看"到底发出去的是什么"**。九成的"AI 不听话"其实是提示词拼错了。

---

# 第 6 部分：常见错误

| 现象 | 原因 | 怎么办 |
|---|---|---|
| `3006 AI 没有返回任何评分` | 模型没按 schema 返回 | 看日志里的完整异常；检查提示词有没有拼坏 |
| `score` 全是 0 | AI 返回的 `questionIndex` 和题目下标对不上 | 打印 `byIndex` 的 key，确认模型给的是 0/1/2 还是 1/2/3 |
| `hitPoints` 全是"回答正确"这类空话 | 提示词里"必须具体"那句被漏掉了 | 检查 `interview-evaluation-user.st` |
| 分数普遍偏高（都给 8-9） | 字段顺序没按"证据在前"排 | 检查 `QuestionScore` 的 record 字段顺序 |
| `duplicate key violates uk_ir_session` | 并发评分 | 已经在 `catch` 里处理；如果还报，说明 catch 不在事务外面 |
| `violates check constraint ck_iqs_score` | 没做 clamp | 检查 `clamp` 是否在赋值前调用 |
| 报告里 `overallScore` 和逐题分对不上 | 总分是 AI 给的 | 改成代码算 |
| `questionScores` 顺序乱 | 没排序 | `toResponse` 里的 `.sorted(...)` |
| 启动报 `Could not resolve placeholder 'spring.ai.deepseek.chat.options.model'` | 配置里没这个键 | `@Value` 的冒号后面要写默认值 |

---

# 第 7 部分：这一步做完，你手上有什么

```
POST /api/interview/sessions/{id}/evaluate
  → 一次 AI 调用
  → 10 行逐题分（分数 / 命中 / 遗漏 / 评语）
  → 1 行报告（总分 / 总结 / 优势 / 建议）

重复调用：秒回，不花钱
没答完：3004
并发：唯一约束兜底
```

**这就是 M6 的核心。** 剩下 M6-3 只是"把它读出来给前端看"——纯查询，没有新概念。

---

# 第 8 部分：做完之后的三个思考题

这三个问题没有标准答案，但想清楚了对 M7（RAG）很有帮助：

1. **`hitPoints` 里 AI 引用的"证据"，真的是候选人说的原话吗？**
   模型有时会"总结"而不是"引用"。如果它总结错了，你可能要加一条校验：把 `hitPoints` 拿去原回答里搜一下关键词。

2. **同一份回答，两种模型给的分数差多少？**
   评完分后把 `model` 列改成别的名字再评一次（先删报告），对比一下。**你会直观感受到"分数不是一个客观事实"** —— 这也是为什么我们要存 `model`。

3. **如果候选人的回答里有一句"我不知道"，模型会给几分？**
   这涉及到"诚实"该不该加分。想清楚这个，你就理解了"评分标准"为什么是要人来定的。
