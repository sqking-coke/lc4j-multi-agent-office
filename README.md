# 🤖 Agent Office — 多 Agent 协同办公 AI 中台

> 一个基于任务分解 + 专用 Prompt 路由的 LLM 编排系统。
> 核心思路：同一个 LLM，不同的 Prompt + 温度 + 质量流程 = 远超单次调用的输出质量。

---

## 这个项目实际解决了什么问题

### 什么问题：单次 LLM 调用搞不定复杂任务

你问 ChatGPT「帮我审查这段代码 + 写成周报 + 翻译成英文」，它会糊弄——三件事各做 40 分。

**不是 LLM 能力不够，是一次调用的注意力不够。** 一个 Prompt 塞不进三个专业的上下文，一个温度值无法同时满足「代码审查要严谨(0.2)」和「翻译要流畅(0.3)」。

### 解决方式：同一 LLM，戴不同帽子

```
不用本系统：用户 → 一个大 Prompt → LLM → 凑合结果（串行、无分工、无质检）

使用本系统：用户 → 拆任务 → ┌ 代码审查 Prompt (temp=0.2)
                            ├ 文档处理 Prompt (temp=0.4)
                            ├ 数据报告 Prompt (temp=0.2)
                            └ ...
                            ↓
                         质检循环（不达标重做）
                            ↓
                         自动聚合 → 一份结构化报告
```

| 维度 | 单次 LLM 调用 | 本系统 |
|------|-------------|--------|
| Prompt | 一个大杂烩 | 每个子任务独立专用 |
| 温度 | 一个值 | 每 Agent 独立（0.2~0.4） |
| 执行 | 串行 | 无依赖子任务并行（总耗时 = 瓶颈） |
| 质量 | 输出即终点 | 不达标自动循环重做 |
| 容错 | 挂了全丢 | 单个失败不影响其他，部分成功也出报告 |
| 成本 | 不可控 | Token 预算 80% 预警 / 100% 熔断 |

### 实际情况

- **现阶段**：6 个「Agent」底层是同一个 DeepSeek 模型，本质是 **任务分解 + Prompt 路由 + 聚合** 的工程系统
- **不是**：6 个不同的 AI 大脑在协作
- **但是**：架构上 Agent 接口与模型解耦——`TranslationAgent` 明天换 DeepL、`CodeReviewAgent` 换 Claude，编排层一行不用改

### 这个项目适合展示什么

| 适合展示 | 不适合展示 |
|---------|-----------|
| LLM 应用的工程化架构设计 | 多模型 AI 协作（目前单模型） |
| StateGraph 声明式编排 | RAG 效果（知识库为空） |
| Agent 抽象 + 能力注册机制 | 生产级性能和稳定性 |
| JWT + 多租户 + Prometheus 全套基础设施 | 真实业务数据 |
| 从 Prompt 到分布式编排的完整链路 | AI 算法创新 |

---

## 技术栈

| 层次 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.5 + Java 21 | 虚拟线程、GraalVM 就绪 |
| LLM 接入 | LangChain4j 1.0-beta3 + DeepSeek | AiServices 接口化调用，JSON Schema 自动映射 |
| 编排 | LangGraph4j 1.2.3 | StateGraph DAG，Channel Reducer 自动状态合并 |
| 持久层 | MyBatis-Plus 3.5 + MySQL | 多租户 SQL 拦截、分页 |
| 安全 | Spring Security + JJWT 0.12 | 无状态 JWT + BCrypt |
| API 文档 | Knife4j 4.5 (OpenAPI 3) | 21 个 API 自动文档 |
| 可观测性 | Micrometer + Prometheus | Agent 耗时/成功率/Token 消耗指标 |
| 前端 | 纯 HTML + Tailwind CSS CDN | 零构建工具，Spring Boot 直接托管 |

---

## 快速启动

### 1. 准备数据库

```bash
mysql -u root -p < init.sql
```

### 2. 配置 LLM API Key

编辑 `src/main/resources/application.yml`：

```yaml
llm:
  api-key: ${LLM_API_KEY:你的API-Key}
```

支持任何兼容 OpenAI 协议的 API（DeepSeek / OpenAI / 本地 Ollama 等）。

### 3. 启动

```bash
mvn spring-boot:run
```

### 4. 访问

| 页面 | URL | 说明 |
|------|-----|------|
| 功能验证 | http://localhost:8085 | 提交任务 → 观察 StateGraph 编排全链路 |
| API 文档 | http://localhost:8085/doc.html | Knife4j OpenAPI 3 |
| 健康检查 | http://localhost:8085/actuator/health | `{"status":"UP"}` |
| Prometheus | http://localhost:8085/actuator/prometheus | 全部指标 |

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |

---

## 核心设计

### 架构：四层解耦

```
Controller → Service → StateGraph 编排层 → Agent 执行层
                         ↑                    ↑
                    声明式 DAG           BizAgent 接口
                    框架管状态           与模型解耦
```

### 编排：StateGraph DAG

```
decompose → routeByCoopMode → agent_executor → routeForLoop → aggregator → END
   ↑              ↑                ↑                ↑              ↑
 关键词拆任务   串行/并行路由     Agent 执行       质量不够→重做   聚合成报告
```

`agent_executor` 内部使用**依赖拓扑分层并行**：`buildDependencyLevels()` 按 `dependsOn` 将子任务分层，同层无依赖的并发执行（wall clock ≈ max），跨层串行等待。工单分析 2 个无依赖子任务 wall clock 从 ~3.8s 降到 ~2.1s。

### Agent 能力匹配

```
GraphNodes 按 capability 标签查找 Agent → AgentRegistry.findBestForCapability()
                                            ↓
                              同标签中选 priority 最优的
```

### 6 个 Agent（6 套专用 Prompt + 温度配置）

| Agent | 能力标签 | 温度 | 典型触发词 |
|-------|---------|------|-----------|
| 代码审查 | code_review, bug_detection, security_scan | 0.2 | 代码/审查/review |
| 工单处理 | ticket_process | 0.3 | 工单/ticket |
| 文档处理 | doc_analysis, doc_summary, doc_rewrite | 0.4 | 文档/总结/改写 |
| 智能答疑 | rag_qa, knowledge_search | 0.3 | 问答/答疑 |
| 数据报告 | data_report, data_summary, trend_analysis | 0.2 | 周报/月报/数据 |
| 翻译 | translation, i18n, multilingual | 0.3 | 翻译/translate/国际化 |

---

## 项目结构

```
src/main/java/com/agentoffice/
├── lc4j/                          # ★ 核心层（LangChain4j + LangGraph4j）
│   ├── agent/
│   │   ├── impl/                   # 6 个 Agent 实现（每个 ~80 行）
│   │   ├── model/                  # LLM 输出结构（Java Record）
│   │   └── service/                # AiServices 接口（LLM 调用契约）
│   ├── config/                     # LLMConfig / GraphConfig / ThreadPoolConfig
│   ├── controller/                 # V2 控制器（StateGraph 入口）
│   └── graph/                      # ★ 编排核心（重点阅读）
│       ├── OfficeAgentState.java   #   状态 Schema + Channel Reducer
│       ├── OfficeStateGraph.java   #   StateGraph DAG 构建 + 入口
│       ├── GraphNodes.java         #   decompose / execute / aggregate
│       └── RouteFunctions.java     #   协同模式 / 循环退出路由
├── agent/                          # Agent 抽象层（BizAgent 接口 + 注册中心）
├── controller/ service/            # V1 REST 控制器 + 业务服务
├── security/                       # JWT Token + Spring Security
├── config/                         # MyBatis-Plus 多租户拦截器
├── monitor/                        # 告警服务（Webhook/钉钉/企微）
└── prompt/                         # Prompt 模板引擎 {{variable}}
```

---

## 如何新增 Agent

完整 Step-by-Step 见 [开发指南](docs/DEVELOPMENT.md)，快速版：

```
1. model/XxxResult.java      ← 定义 LLM 输出字段
2. prompts/xxx.txt            ← 编写 System Prompt
3. service/XxxService.java    ← 定义 AiServices 接口
4. impl/XxxAgent.java         ← 实现 BizAgent + 自注册
5. config/LLMConfig.java      ← 加一个 @Bean
6. graph/GraphNodes.java      ← 加关键词触发分支
```

**维护速查**：

| 要改什么 | 去哪个文件 |
|---------|-----------|
| 能力标签/温度/优先级/Token 预算 | `impl/XxxAgent.java` → `AgentConfig` |
| System Prompt | `prompts/xxx.txt` |
| LLM 输出字段 | `model/XxxResult.java` |
| 关键词触发 | `graph/GraphNodes.java` → `decomposeByKeyword()` |

---

## 局限与改进方向

| 当前局限 | 改进方向 |
|---------|---------|
| 所有 Agent 共用一个模型 | 按 Agent 类型路由到不同模型（Claude 审代码、DeepL 翻译） |
| 关键词拆任务过于简单 | 引入 LLM 自动分解任务（DSPy / ReAct） |
| Agent 之间无通信 | 加入 Agent-to-Agent 消息传递（类似 AutoGen GroupChat） |
| 知识库为空（RAG 无数据） | 接入向量数据库（Milvus / Pinecone）做语义检索 |
| 同步阻塞返回结果 | 改为 SSE 流式推送每个 Agent 实时进度 |
| 子任务依赖耗时仍串行 | 已通过拓扑分层并行优化（同层并发），但跨层仍需串行 |

---

## 文档导航

- 📘 [架构设计](docs/ARCHITECTURE.md)
- 📗 [API 参考](docs/API.md)
- 📙 [开发指南](docs/DEVELOPMENT.md)

---

## License

MIT
