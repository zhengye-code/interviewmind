# 附-01：`TypeReference` 到底是什么，怎么用

> **为什么要单独讲这个**：`TransformService.fromJson` 的 bug 就出在这里 —— 方法签名看着对，运行时还是 `ClassCastException`。
>
> 这一篇把"JSON 反序列化时类型怎么会丢、怎么找回来"讲透。

---

# 第 0 部分：一句话结论

**反序列化（JSON → 对象）需要知道"目标是什么类型"；而 Java 的泛型在运行时会擦除，`List<X>` 里的 `X` 会丢。`TypeReference`（或 `JavaType`）就是用来把这个类型"带"过去的。**

序列化（对象 → JSON）**不需要**，因为它看的是对象的真实类型。

---

# 第 1 部分：问题是怎么产生的

## 1.1 先看一个能跑的例子

```java
String json = "{\"categoryKey\":\"JAVA\",\"label\":\"Java\",\"quota\":3}";

CategoryQuota quota = mapper.readValue(json, CategoryQuota.class);
// quota 是货真价实的 CategoryQuota
```

`CategoryQuota.class` 是**具体类型**，Jackson 知道该 new 什么、填什么字段。没问题。

## 1.2 换成 List 就出事了

```java
String json = "[{\"categoryKey\":\"JAVA\"},{\"categoryKey\":\"MYSQL\"}]";

List<CategoryQuota> list = mapper.readValue(json, List.class);   // ❌ 传的是 List.class
```

**传进去的是 `List.class`，它只说"这是个列表"，没说"列表里装什么"。**

Jackson 能确定外层是 `ArrayList`，但元素类型它不知道 —— 于是按最保守的方式处理：**每个 JSON 对象读成 `LinkedHashMap`**。

结果：

```java
List list = mapper.readValue(json, List.class);
list.get(0).getClass();   // class java.util.LinkedHashMap   ← 不是 CategoryQuota
```

## 1.3 为什么"看起来"没报错

因为 `List.class` 是**原始类型**（raw type），编译器不会做元素检查。等你真的当 `CategoryQuota` 用时：

```java
List<CategoryQuota> list = mapper.readValue(json, List.class);   // 有一行黄色警告，但能编译
CategoryQuota q = list.get(0);        // 编译器插入 checkcast CategoryQuota
                                      // → 运行时：LinkedHashMap 不是 CategoryQuota → ClassCastException
```

**编译通过、启动正常、第一次取元素时炸。** 这就是你的项目现在的状态。

---

# 第 2 部分：`TypeReference` 是干什么的

## 2.1 它解决的问题

给 Jackson 一个"**带泛型参数的完整类型**"，让它知道列表里装什么。

```java
new TypeReference<List<CategoryQuota>>() {}
```

## 2.2 为什么必须写成这样（三个符号都有用）

```java
new TypeReference<List<CategoryQuota>>() { }
//  ↑                ↑                    ↑
//  关键字            泛型实参             空的大括号
```

| 部分 | 作用 |
|---|---|
| `new TypeReference<...>()` | 创建实例 |
| `<List<CategoryQuota>>` | **告诉它完整类型** |
| `{ }` | **创建一个匿名子类**（关键） |

### 为什么 `{ }` 不能少

`TypeReference` 是个**抽象类**（构造器是 `protected`），你不能直接 `new` 它。`{ }` 表示"创建一个匿名子类"，这才合法。

### 为什么匿名子类就能记住类型

这是它唯一的工作原理，值得看一遍：

```java
new TypeReference<List<CategoryQuota>>() {}
```

编译器会生成一个无名类，相当于：

```java
class My$1 extends TypeReference<List<CategoryQuota>> { }
```

**父类签名 `List<CategoryQuota>` 被写进了 class 文件**（泛型擦除擦的是"方法体里的泛型"，但**类/父类的泛型签名会被保留**，因为反射需要它）。

运行时 Jackson 干的是：

```java
Type type = getClass().getGenericSuperclass();
// 读出：TypeReference<List<CategoryQuota>>
// 解析出：List，元素是 CategoryQuota
```

**所以：类型信息是靠"继承关系"带进去的，不是靠方法参数。**

## 2.3 为什么泛型方法里失效

这就是你写的那版：

```java
public static <T> List<T> fromJson(String json) {
    return mapper.readValue(json, new TypeReference<List<T>>() {});
}
```

这个匿名子类的父类签名是 **`TypeReference<List<T>>`**，其中 `T` 是**类型变量**（TypeVariable），不是具体类。

- 方法**内部**不知道调用方把 `T` 推成了什么 —— 那个推断只存在于编译器的脑子里
- Jackson 拿到一个"未赋值的类型变量"，只能退化成 `Object`
- 于是元素变成 `LinkedHashMap`

而调用方写：

```java
List<CategoryQuota> plan = TransformService.fromJson(json);
```

编译器信了方法签名（`<T>` 被推断为 `CategoryQuota`），在 `plan.get(0)` 处插了一个 `checkcast CategoryQuota` —— **一取元素就 CCE**。

> **一句话记住：`TypeReference` 里的类型必须是"写死的具体类型"，不能是类型变量 `T`。**

---

# 第 3 部分：三种正确写法

## 写法 A：按用途拆方法（**推荐，最不容易写错**）

每个方法内部写死具体类型。你的项目**只需要两个**：

```java
/** plan_json → List<CategoryQuota> */
public static List<CategoryQuota> parseQuotaPlan(String json) {
    if (json == null || json.isBlank()) {
        return List.of();
    }
    try {
        return OBJECT_MAPPER.readValue(json, new TypeReference<List<CategoryQuota>>() {});
    } catch (JacksonException e) {
        log.warn("解析配额 JSON 失败,json={}", json);
        return List.of();
    }
}

/** 字符串数组 JSON → List<String>（答案要点、优势、建议、命中/遗漏 都用它） */
public static List<String> parseStringList(String json) {
    if (json == null || json.isBlank()) {
        return List.of();
    }
    try {
        return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
    } catch (JacksonException e) {
        log.warn("解析字符串数组失败,json={}", json);
        return List.of();
    }
}
```

调用点变得非常干净：

```java
List<CategoryQuota> plan = TransformService.parseQuotaPlan(sessionEntity.getPlanJson());
List<String> points = TransformService.parseStringList(entity.getExpectedPoints());
```

**为什么推荐**：类型字面量写死在方法体里，**不可能被误用**。谁调用都不会出错。

**代价**：每多一种类型，多一个方法。但你的项目解析的类型是固定几种，不会膨胀。

## 写法 B：把 `TypeReference` 当参数传进来

```java
public static <T> List<T> fromJson(String json, TypeReference<List<T>> type) {
    if (json == null || json.isBlank()) {
        return List.of();
    }
    try {
        return OBJECT_MAPPER.readValue(json, type);
    } catch (JacksonException e) {
        return List.of();
    }
}
```

调用：

```java
List<CategoryQuota> plan = TransformService.fromJson(
        json, new TypeReference<List<CategoryQuota>>() {});
```

**为什么这样能行**：匿名子类是在**调用方**创建的，那里 `List<CategoryQuota>` 是具体类型，能被写进 class 文件。

**缺点**：调用点变得很长，而且**调用方一旦也在泛型方法里，又会失效**。所以它只是"能行"，不是"好用"。

## 写法 C：用 `JavaType`（不用 `TypeReference`）

Jackson 内部其实会把 `TypeReference` 转成 `JavaType`。你也可以**直接构造**它，不需要匿名子类：

```java
public static <T> List<T> parseList(String json, Class<T> elementType) {
    if (json == null || json.isBlank()) {
        return List.of();
    }
    try {
        JavaType type = OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(List.class, elementType);
        return OBJECT_MAPPER.readValue(json, type);
    } catch (JacksonException e) {
        log.warn("解析 JSON 数组失败,json={}", json);
        return List.of();
    }
}
```

调用：

```java
List<CategoryQuota> plan = TransformService.parseList(json, CategoryQuota.class);
List<String> points  = TransformService.parseList(json, String.class);
```

**为什么这个方法是"真泛型"**：类型信息是通过 **`Class` 对象**在运行时传进来的（`CategoryQuota.class` 是真实存在的 Class，不会被擦除），而不是靠类型变量猜。

**Jackson 3 的 import**（我验证过）：

```java
import tools.jackson.databind.JavaType;
```

`getTypeFactory()` 返回的是 `tools.jackson.databind.type.TypeFactory`，一般不用单独 import。

**适用场景**：解析类型很多、不想每加一种就写一个方法时。

---

# 第 4 部分：`toJson` 为什么不用改

```java
public static String toJson(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
}
```

**序列化方向不需要类型信息**，因为对象**本身就是活的**：

```java
List<CategoryQuota> plan = ...;      // 列表里每个元素是真实的 CategoryQuota 对象
toJson(plan);                        // Jackson 直接读每个对象的 getClass() → 拿到真实字段
```

**擦除影响的是"从 JSON 造对象"（需要知道造什么类型），不影响"把已有对象写成 JSON"（类型就在对象身上）。**

所以：

| 方向 | 需要类型信息吗 | 受影响吗 |
|---|---|---|
| `readValue`（JSON → 对象） | ✅ 需要 | ❌ 受影响，必须用 TypeReference / JavaType |
| `writeValueAsString`（对象 → JSON） | ❌ 不需要 | ✅ 不受影响 |

**`toJson` 保持原样就行。**

---

# 第 5 部分：你的项目要改哪里

## 5.1 `TransformService` 改造后（用写法 A）

`src/main/java/com/interviewmind/modules/interview/utils/TransformService.java`

```java
package com.interviewmind.modules.interview.utils;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试模块的 JSON 转换工具。
 *
 * ⚠️ 注意：这里故意不用泛型方法 <T> List<T> fromJson(...)，
 *    因为 TypeReference<List<T>> 里的 T 是类型变量，运行时会被擦除成 Object，
 *    结果元素变成 LinkedHashMap，取元素时抛 ClassCastException。
 *    正确做法：每个方法内部写死具体类型。
 */
@Slf4j
public final class TransformService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TransformService() {
    }

    /** 分类 key → 展示名 */
    public static Map<String, String> categoryLabels(List<CategoryQuota> plan) {
        Map<String, String> labels = new HashMap<>();
        for (CategoryQuota quota : plan) {
            labels.put(quota.categoryKey(), quota.label());
        }
        return labels;
    }

    /** plan_json → List<CategoryQuota>；解析失败降级为空列表 */
    public static List<CategoryQuota> parseQuotaPlan(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<CategoryQuota>>() {
            });
        } catch (JacksonException e) {
            log.warn("解析配额 JSON 失败,json={}", json);
            return List.of();
        }
    }

    /** 字符串数组 JSON → List<String> */
    public static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JacksonException e) {
            log.warn("解析字符串数组失败,json={}", json);
            return List.of();
        }
    }

    /** 任意对象 → JSON 字符串（序列化不需要类型信息） */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化失败", e);
        }
    }
}
```

## 5.2 调用点要改的地方

| 文件 | 行 | 原来 | 改成 |
|---|---|---|---|
| `InterviewQuestionService` | 79 | `TransformService.fromJson(planJson)` | `TransformService.parseQuotaPlan(planJson)` |
| `InterviewQuestionService` | 137 | `TransformService.fromJson(planJson)` | `TransformService.parseQuotaPlan(planJson)` |
| `InterviewEvaluateService` | 56 | `TransformService.fromJson(expectedPoints)` | `TransformService.parseStringList(...)` |
| `InterviewEvaluateService` | 105 | `fromJson(hitPointsJson)` | `parseStringList(...)` |
| `InterviewEvaluateService` | 106 | `fromJson(missedPointsJson)` | `parseStringList(...)` |
| `InterviewEvaluateService` | 123 | `fromJson(strengthsJson)` | `parseStringList(...)` |
| `InterviewEvaluateService` | 124 | `fromJson(suggestionsJson)` | `parseStringList(...)` |
| `InterviewEvaluateService` | 62 | `toJson(items)` | **不变**（序列化） |
| `InterviewEvaluateService` | 71/72 | `toJson(...)` | **不变** |
| `InterviewEvaluateService` | 90/91 | `toJson(...)` | **不变** |

## 5.3 顺带修掉的另一个问题

`InterviewQuestionService:79` 现在写的是：

```java
List<CategoryQuota> plan = TransformService.fromJson(sessionEntity.getPlanJson());
```

改成 `parseQuotaPlan` 之后，**编译期就能保证类型对了**（返回类型就是 `List<CategoryQuota>`），不需要任何强转。

---

# 第 6 部分：怎么验证改对了

## 6.1 单元测试（最可靠）

`src/test/java/com/interviewmind/modules/interview/utils/TransformServiceTest.java`

```java
package com.interviewmind.modules.interview.utils;

import com.interviewmind.modules.interview.model.backendModel.CategoryQuota;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransformServiceTest {

    private static final String PLAN_JSON = """
            [{"categoryKey":"JAVA","label":"Java","priority":"CORE","quota":3},
             {"categoryKey":"MYSQL","label":"MySQL","priority":"CORE","quota":2}]
            """;

    @Test
    void 配额JSON应解析成CategoryQuota而不是Map() {
        List<CategoryQuota> plan = TransformService.parseQuotaPlan(PLAN_JSON);

        // ① 数量对
        assertThat(plan).hasSize(2);

        // ② 元素类型对（这一条才是关键）
        assertThat(plan.get(0)).isInstanceOf(CategoryQuota.class);

        // ③ 真的能调用 getter
        assertThat(plan.get(0).categoryKey()).isEqualTo("JAVA");
        assertThat(plan.get(0).quota()).isEqualTo(3);
    }

    @Test
    void 字符串数组应解析成String列表() {
        List<String> points = TransformService.parseStringList("[\"要点一\",\"要点二\"]");

        assertThat(points).containsExactly("要点一", "要点二");
        assertThat(points.get(0)).isInstanceOf(String.class);
    }

    @Test
    void 空JSON应降级为空列表() {
        assertThat(TransformService.parseQuotaPlan(null)).isEmpty();
        assertThat(TransformService.parseQuotaPlan("")).isEmpty();
        assertThat(TransformService.parseStringList("  ")).isEmpty();
    }
}
```

### ⚠️ 为什么第 ② 条断言不能省

这是这次 bug 最值得记住的教训：

```
bug 版本的输出：size = 2        ← 数量是对的！
然后 plan.get(0) 抛 CCE
```

**如果测试只断言 `hasSize(2)`，bug 版本的测试会"通过"。**

所以断言必须落在**能区分"看起来对"和"真的对"**的地方：

| 断言 | bug 版本 | 正确版本 |
|---|---|---|
| `hasSize(2)` | ✅ 通过 | ✅ 通过 |
| `isInstanceOf(CategoryQuota.class)` | ❌ 失败 | ✅ 通过 |
| `plan.get(0).categoryKey()` | ❌ CCE | ✅ 通过 |

**测试的价值不在于"跑一遍没报错"，而在于"能抓住错误"。**

## 6.2 也可以用我之前的临时程序验证

如果你想要一个"肉眼可见"的确认，可以在 `main` 方法里打印：

```java
System.out.println(plan.get(0).getClass().getName());
```

正确时输出：

```
com.interviewmind.modules.interview.model.backendModel.CategoryQuota
```

---

# 第 7 部分：常见错误对照表

| 写法 | 结果 | 原因 |
|---|---|---|
| `readValue(json, List.class)` | `List<LinkedHashMap>` | 只知道是列表，不知道元素类型 |
| `new TypeReference<List<CategoryQuota>>()` （没有 `{}`） | 编译不过 | 抽象类不能实例化 |
| `new TypeReference<>() {}` | 返回 `Object`/`Map` | 菱形推断不出具体类型 |
| `public static <T> List<T> f()` + `new TypeReference<List<T>>() {}` | `List<LinkedHashMap>` | `T` 是类型变量，运行时被擦除成 Object |
| `new TypeReference<List<CategoryQuota>>() {}` | ✅ 正确 | 类型写死在父类签名里 |
| `TypeFactory.constructCollectionType(List.class, CategoryQuota.class)` | ✅ 正确 | 用 Class 在运行时构造类型 |
| `writeValueAsString(list)` | ✅ 正确 | 序列化不需要类型信息 |

---

# 第 8 部分：一句话总结

```
JSON → 对象 ：必须告诉 Jackson "目标类型"      → TypeReference / JavaType
对象 → JSON ：不用告诉，对象自己知道            → 直接写

而 TypeReference 的类型必须"写死"，
放到泛型方法里用 T，等于没写。
```
