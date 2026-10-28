# ADR-001：多 Provider 接入与 API Key 管理策略

- **状态**：已采纳
- **日期**：2026-10-28
- **关联阶段**：M2（AI 接入）、M6（设置页）

---

## 背景

项目需要接入大语言模型，并且未来会支持多个厂商（DeepSeek、Kimi、通义、GLM、本地 LM Studio 等）。

核心疑问有三个：

1. 要不要自己写一套「多 Provider 注册表」？
2. 新厂商出现时，接入成本会不会很高？
3. API Key 放环境变量还是数据库？

---

## 决策

### 决策 1：所有 Provider 统一走「OpenAI 兼容协议」

**不按厂商引入各自的官方 starter，统一使用 `spring-ai-starter-model-openai` +
`OpenAiChatModel`，只通过 `base-url` / `api-key` / `model` 三个参数区分厂商。**

事实依据：主流厂商都已提供 OpenAI 兼容端点。

| 厂商 | base-url |
|---|---|
| DeepSeek | `https://api.deepseek.com` |
| Moonshot / Kimi | `https://api.moonshot.cn/v1` |
| 阿里云百炼 / DashScope | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| 智谱 / GLM | `https://open.bigmodel.cn/api/coding/paas/v4` |
| LM Studio（本地） | `http://localhost:1234` |

**收益**：新增厂商 = 增加 3 行配置，Java 代码零改动。

**代价**：兼容不是 100%，需要一层薄的适配工具处理差异：

- base-url 是否已带版本路径（有的要补 `/v1`，有的自带 `/v4`）
- `max_tokens` 等参数名、`tools` / `response_format` 支持度、流式格式差异
- Embedding 端点路径与向量维度不同

对应实现：`ApiPathResolver.resolveVersionedBaseUrl()`（补版本路径 + 去尾斜杠 + 超时设置）。

---

### 决策 2：M3~M5 阶段不使用多 Provider 注册表

**当前只接一个 DeepSeek，Key 从环境变量读取，不引入运行时切换机制。**

理由：

- 目前只有一个 Provider 在用，多 Provider 是「没有需求的功能」
- 运行时切换的价值要等设置页做出来才能体现
- 为尚未到来的需求提前抽象，属于过度设计（over-engineering）

**不做的事**：不写 `LlmProviderRegistry`、不做热更新、不引入 Spring Cloud。

---

### 决策 3：M6 设置页阶段，API Key 加密后存数据库，主密钥留环境变量

**数据库存密文，主密钥（Master Key）只存在于环境变量 / 部署配置中。**

两层 key 必须区分清楚：

| | API Key | 加密主密钥 |
|---|---|---|
| 来源 | 厂商发放 | 系统自行生成 |
| 数量 | 每个厂商一个 | 全系统唯一 |
| 存放 | 加密后进数据库 | 环境变量 / KMS |

**为什么主密钥不能也存数据库**：若两者同处一地，数据库泄露即等于明文泄露，加密失去意义。
主密钥与密文存放位置分离，才能构成两个独立的信任边界。

**加密实现要点**：

- 算法：AES/GCM/NoPadding
- 每条记录使用独立 nonce（禁止固定 IV）
- 接口返回给前端时必须脱敏（形如 `****ef11`）
- 日志中禁止输出明文 Key
- 主密钥「部署后保持不变」——它是解密历史数据的前提

**存储载体**：数据库（`llm_provider_config` 表），而非配置文件。

理由：设置页 CRUD 天然是数据库操作；配置文件与数据库双写会引入同步问题。

---

### 决策 4：环境变量退化为「初始化种子」

**首次启动时若数据库中没有 Provider 记录，则从环境变量读取一次、加密后写入数据库；
之后一律以数据库为准。**

收益：

- 首次部署仍然只需提供一个 `DEEPSEEK_API_KEY` 即可启动
- 开发期执行 `docker compose down -v` 清库后，配置能从环境变量自动重建
- 后续所有变更都在设置页完成，无需再碰环境变量

---

## 后果

### 正面

- 新增模型厂商的成本降到「改配置」
- 设置页可独立管理模型，不需要改代码或重新部署
- 密钥泄露面显著缩小

### 负面 / 需要接受的代价

- 放弃 Spring AI 自动配置的便利，客户端需自行构建（约 40~80 行）
- 配置进入数据库后，备份/迁移时密钥随之迁移，需要额外注意
- 主密钥丢失 = 所有已加密的 API Key 无法恢复，必须配套「重置并重新录入」的运维预案
- 兼容协议的边缘差异需要长期维护一层薄适配

---

## 备选方案与放弃原因

| 方案 | 内容 | 放弃原因 |
|---|---|---|
| A. Spring AI 自动配置 + 重启切换 | 改 `application.properties` 后重启 | 设置页要求免重启 |
| B. 多个官方 starter + `@Qualifier` | 各厂商用各自 starter | 中文厂商大多无官方 starter；加厂商仍要改代码 |
| C. `@RefreshScope` + Spring Cloud Context | 借助 spring-cloud 热刷新 bean | 自动配置的 `ChatModel` 不受 `@RefreshScope` 管理，仍需自行构建；额外引入整套 Spring Cloud |
| D. LiteLLM / one-api 代理 | 外部代理收敛为单一 OpenAI 端点 | **推荐作为 M6 的优先备选**：省掉注册表与密钥管理代码，且自带重试、降级、限流、成本统计。缺点是需多部署一个服务、多一跳网络 |
| E. 密钥明文存数据库 | 省掉加密逻辑 | 数据库备份 / 日志 / 排查过程中极易泄露 |

---

## 后续动作

- [ ] M3~M5：继续使用环境变量 `DEEPSEEK_API_KEY`
- [ ] M6 前重新评估：设置页走「决策 3 自建加密」还是「备选 D 代理方案」
- [ ] M6：设计 `llm_provider_config` 表结构（base_url / model / api_key_ciphertext / api_key_nonce / enabled / builtin）
- [ ] M6：实现 bootstrap seed 逻辑（决策 4）
