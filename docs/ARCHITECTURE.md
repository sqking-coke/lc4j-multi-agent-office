# 架构设计 — Agent Office

## 设计前提

当前所有「Agent」底层复用**同一个 LLM**（默认 DeepSeek v4-flash）。系统的核心价值不在于多模型协作，而在于：

1. **任务分解与路由**：将一个复杂任务拆成多个子任务，每个子任务用**专用 Prompt + 独立温度**调用同个 LLM
2. **并行编排**：无依赖子任务并行执行，总耗时 = 最慢的那个（而非所有之和）
3. **质量循环**：审查 Agent 打分，不达标自动重做，而非一次输出即止
4. **架构解耦**：每个 Agent 的 AiServices 接口独立注册为 Spring Bean，允许未来按 Agent 类型切换不同模型或服务

## 四层架构

```
┌──────────────────────────────────────────────────────────────┐
│                    1. Controller Layer                       │
│  TaskGraphController(/api/v2)  TaskController(/api/v1)       │
│  AuthController  AgentController  ReportController  ...      │
│  职责：接收 HTTP 请求，参数校验，调用 Service，返回 JSON      │
└─────────────────────────┬────────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────────┐
│                    2. Business Layer                         │
│  TaskService  AgentService  ApprovalService  ReportService   │
│  TokenBudgetService  PromptService  KnowledgeBaseService     │
│  职责：业务逻辑编排、DB 持久化、Token 预算检查、告警触发      │
└─────────────────────────┬────────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────────┐
│                 3. Orchestration Layer (核心)                 │
│  OfficeStateGraph  →  StateGraph DAG 构建                    │
│  GraphNodes        →  decompose / execute / aggregate        │
│  RouteFunctions    →  coopMode / loop 条件路由               │
│  OfficeAgentState  →  共享状态 (Channel Reducer)             │
│  职责：Agent 调度、状态管理、协同模式路由、结果聚合           │
└─────────────────────────┬────────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────────┐
│                   4. Agent Layer                             │
│  BizAgent Interface  →  execute(itemId, content, context)    │
│  6 个 Agent 实现    →  CodeReview / Ticket / Document /      │
│                        RagQa / DataReport / Translation      │
│  AiServices 接口     →  @SystemMessage + Record 返回类型     │
│  职责：LLM 调用、结果结构化、健康检查                        │
└──────────────────────────────────────────────────────────────┘
```

## 编排核心：StateGraph DAG

```
                 ┌──────────┐
                 │ decompose │  ← 关键词匹配分解子任务
                 └─────┬────┘
                       │
              ┌────────▼────────┐
              │ routeByCoopMode │ ← 根据协同模式路由
              └──┬──────┬──────┘
                 │      │          │
        ┌────────▼─┐ ┌──▼──────┐ ┌▼───────────┐
        │  fanout   │ │ agent_  │ │ condition_  │
        │  (PARALLEL)│ │executor │ │  check      │
        └────┬──────┘ │(SERIAL) │ │(CONDITION)  │
             │        └──┬──────┘ └──┬─────────┘
             │           │           │
             └───────────┼───────────┘
                         │
                    ┌────▼────┐
                    │ agent_   │ ← Agent 执行入口
                    │ executor │
                    └────┬────┘
                         │
               ┌─────────▼─────────┐
               │  routeForLoop     │ ← 循环判定
               └──┬───────────┬────┘
         继续循环  │           │ 退出
      ┌───────────┘           └──────────┐
      ▼                                  ▼
┌──────────┐                      ┌────────────┐
│  agent_   │                      │ aggregator │ ← 聚合报告
│ executor  │                      └──────┬─────┘
└──────────┘                             │
                                    ┌────▼────┐
                                    │   END    │
                                    └─────────┘
```

### StateGraph 节点说明

| 节点 | 实现 | 职责 |
|------|------|------|
| `decompose` | `GraphNodes.decomposeNode()` | 关键词匹配 → 子任务列表 → 推断协同模式 |
| `fanout` | `GraphNodes.fanoutNode()` | 并行的入口标记 |
| `agent_executor` | `GraphNodes.executeAgentNode()` | 按依赖拓扑分层并行执行子任务（同层并发，跨层串行） |
| `condition_check` | Lambda (空操作) | 条件协同模式入口（预留） |
| `aggregator` | `GraphNodes.aggregateNode()` | 聚合所有 Agent 结果 → 生成 Markdown 报告 |

### 路由函数

| 函数 | 决策逻辑 |
|------|---------|
| `routeByCoopMode()` | PARALLEL→fanout, SERIAL/RELAY/LOOP→agent_executor, CONDITION→condition_check |
| `routeForLoop()` | loopCount>=maxLoop → aggregator, qualityScore>=0.8 → aggregator, 其它 → agent_executor |
| `routeByScore()` | 根据 nextAction 字段路由（预留） |

### 并行执行策略

`executeAgentNode` 使用依赖拓扑分层并行算法 `buildDependencyLevels()`：

```
子任务依赖关系:        
  工单分析(deps=[])    知识检索(deps=[])
       └── Level 0 ──┘  ← 同层并发，wall clock = max(各自耗时)

  数据提取(deps=[])  → 报告生成(deps=[1]) → 报告审查(deps=[2])
   Level 0              Level 1              Level 2
                        ↑ 等待 Level 0 完成   ↑ 等待 Level 1 完成
```

- 同一层级内所有子任务通过 `CompletableFuture.supplyAsync()` + 虚拟线程池并发调用 LLM
- 当前层级全部 `join()` 完成后才进入下一层级
- 有依赖的子任务自动分层串行，无依赖的子任务自动并行
- 实测：工单分析 2 个无依赖子任务 wall clock 从 ~3.8s 降到 ~2.1s（-45%）

### 协同模式推断

### 注册流程

```java
// 每个 Agent 在 @PostConstruct 中自注册
@PostConstruct
public void init() {
    AgentConfig config = AgentConfig.builder()
        .agentCode("code_review_agent")
        .capability(AgentCapability.builder()
            .capabilities(List.of("code_review", "bug_detection", "security_scan"))
            .build())
        .priority(10)     // 优先级（数字越小越优先）
        .maxConcurrency(3) // 最大并发数
        .build();
    registry.register(this, config);
}
```

### 能力匹配

```java
// GraphNodes 通过 capability 字符串匹配 Agent
public Optional<BizAgent> findBestForCapability(String capability) {
    return agents.values().stream()
        .filter(a -> a.getCapability().getCapabilities().contains(capability))
        .sorted(byPriority)  // 同能力选优先级最高的
        .findFirst();
}
```

### 协同模式推断

```java
// decomposeByKeyword() 中：
// - 子任务数 <=1 → SERIAL
// - 子任务有 dependsOn → SERIAL
// - 其它 → PARALLEL
```

## 多租户隔离

```
请求进入 → JwtAuthenticationFilter → 解析 JWT → TenantContext.set(tenantId)
                                                         │
                                          ┌──────────────┘
                                          ▼
                              MyBatis-Plus TenantLineInnerInterceptor
                              自动注入 WHERE tenant_id = ?
                                          │
                                          ▼
                              MdcTaskDecorator.wrap()
                              保证跨线程传播 traceId + tenantId
```

### 排除租户隔离的表

`sys_role`, `sys_permission`, `sys_role_permission`, `task_decompose_template`, `llm_token_daily_stat`, `agent_info`, `agent_prompt_template`, `approval_record`

## JWT 认证流程

```
POST /api/v1/auth/login  {username,password}
    → BCryptPasswordEncoder.matches()
    → HMAC-SHA384 JWT (userId, tenantId, roleCode, permissions)
    → AccessToken (1h) + RefreshToken (7d)

后续请求:
    Authorization: Bearer <accessToken>
    → JwtAuthenticationFilter (OncePerRequestFilter)
    → 解析 Token → SecurityContextHolder
    → TenantContext.set(tenantId)
```

## 可观测性

| 维度 | 实现 | 端点 |
|------|------|------|
| Agent 任务计数 | Micrometer Counter (`agent_task_total`) | `/actuator/prometheus` |
| Agent 错误计数 | Micrometer Counter (`agent_task_errors`) | `/actuator/prometheus` |
| Agent 耗时分布 | Micrometer Timer (`agent_task_duration`) | `/actuator/prometheus` |
| LLM Token 消耗 | Micrometer Counter (`llm_token_consumed`) | `/actuator/prometheus` |
| Token 预算预警 | `TokenBudgetService` 80%/100% | 日志 + Webhook |
| Agent 失败告警 | `AlertService` 连续失败 N 次 | Webhook (企业微信/钉钉) |
| 任务队列积压 | `AlertService` 队列超阈值 | Webhook |

## 技术决策记录

### 为什么用 StateGraph 替代手写 DispatchHub？

| 对比维度 | 手写版 (已废弃) | StateGraph 版 (当前) |
|----------|----------------|---------------------|
| 代码量 | ~990 行 | ~210 行 (-79%) |
| 状态管理 | ConcurrentHashMap + ReadWriteLock | Channel Reducer 自动合并 |
| 控制流 | if-else 分支 (7 个 Strategy) | 声明式 DAG (routeByCoopMode) |
| 并行安全 | 手动加锁 | 框架保证原子性 |
| 可测试性 | 难以单独测试节点 | 每个 Node 独立可测 |
| 可视化 | 无 | DAG 图结构自文档化 |

### 为什么用 AiServices 接口替代手写 LLM 调用？

| 对比维度 | 手写版 | AiServices 版 |
|----------|--------|---------------|
| 每个 Agent LLM 代码量 | ~70 行 | ~5 行 (-93%) |
| JSON 解析 | 手写 try-catch + 重试 | 框架自动序列化 + 重试 |
| 类型安全 | 无 (Map/JsonNode) | Java Record 强类型 |
| Prompt 管理 | 硬编码字符串 | `@SystemMessage(fromResource=...)` |
