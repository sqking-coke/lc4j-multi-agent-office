# 开发指南 — Agent Office

## 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- DeepSeek API Key（或兼容 OpenAI 协议的 LLM）

## 常用命令

```bash
# 编译
mvn compile

# 打包
mvn package -DskipTests

# 启动
mvn spring-boot:run

# 启动（跳过测试编译）
mvn spring-boot:run -DskipTests

# 查看依赖树
mvn dependency:tree

# 清理
mvn clean
```

## 项目结构详解

```
lc4j-multi-agent-office/
├── pom.xml                          # Maven 配置
├── init.sql                         # 数据库初始化 DDL
├── README.md                        # 项目说明
├── docs/                            # 文档
│   ├── ARCHITECTURE.md              # 架构设计
│   ├── API.md                       # API 参考
│   └── DEVELOPMENT.md               # 本文件
└── src/
    ├── main/
    │   ├── java/com/agentoffice/
    │   │   ├── lc4j/                # ★ LangChain4j 重写层
    │   │   │   ├── agent/
    │   │   │   │   ├── impl/        # Agent 实现类
    │   │   │   │   ├── model/       # Result Record
    │   │   │   │   └── service/     # AiServices 接口
    │   │   │   ├── config/          # LLM/Graph/线程池配置
    │   │   │   ├── controller/      # V2 控制器
    │   │   │   └── graph/           # StateGraph 编排核心 ★★
    │   │   ├── agent/               # Agent 抽象层
    │   │   ├── config/              # MyBatis-Plus 配置
    │   │   ├── controller/          # V1 控制器
    │   │   ├── entity/              # 数据库实体
    │   │   ├── mapper/              # MyBatis Mapper
    │   │   ├── monitor/             # 告警服务
    │   │   ├── prompt/              # Prompt 模板引擎
    │   │   ├── schedule/            # 定时任务
    │   │   ├── security/            # JWT + Spring Security
    │   │   ├── service/             # 业务服务
    │   │   └── util/                # 工具类
    │   └── resources/
    │       ├── application.yml      # 应用配置
    │       ├── prompts/             # ★ Agent 提示词模板
    │       │   ├── code-review.txt
    │       │   ├── data-report.txt
    │       │   ├── document-process.txt
    │       │   ├── rag-qa.txt
    │       │   ├── ticket-analyze.txt
    │       │   └── translate.txt
    │       └── static/              # ★ 功能验证页
    │           └── index.html       # 任务触发 + 编排结果展示
    └── test/                        # 测试
```

## 如何新增 Agent（Step-by-Step）

以新增 `TranslationAgent` 为例，完整流程：

### Step 1: 创建 Result Record

```java
// src/main/java/com/agentoffice/lc4j/agent/model/TranslationResult.java
public record TranslationResult(
    String sourceLanguage,
    String targetLanguage,
    String translatedText,
    List<String> alternatives,
    double confidence
) implements Serializable {}
```

### Step 2: 创建 AiServices 接口

```java
// src/main/java/com/agentoffice/lc4j/agent/service/TranslationService.java
public interface TranslationService {
    @SystemMessage(fromResource = "prompts/translate.txt")
    TranslationResult translate(
        @V("sourceLang") String sourceLang,
        @V("targetLang") String targetLang,
        @UserMessage String text
    );
}
```

### Step 3: 编写 Prompt 模板

```text
# src/main/resources/prompts/translate.txt
你是一个专业多语言翻译助手...
要求：准确、流畅、保留原意
输出 JSON 格式...
```

### Step 4: 实现 BizAgent

```java
// src/main/java/com/agentoffice/lc4j/agent/impl/TranslationAgent.java
@Component
@RequiredArgsConstructor
public class TranslationAgent implements BizAgent {

    private final AgentRegistry registry;
    private final TranslationService translationService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
            .agentCode("translation_agent")
            .capability(AgentCapability.builder()
                .capabilities(List.of("translation", "i18n", "multilingual"))
                .build())
            .temperature(0.3).maxTokens(4096).priority(15)
            .build();
        registry.register(this, config);
    }

    @Override
    public String getAgentCode() { return "translation_agent"; }

    @Override
    public AgentCapability getCapability() { /* 返回 capability */ }

    @Override
    public AgentResult execute(String itemId, String content,
                                Map<String, Object> globalContext) {
        TranslationResult result = translationService.translate(
            (String)globalContext.getOrDefault("sourceLang", "auto"),
            (String)globalContext.getOrDefault("targetLang", "Chinese"),
            content
        );
        return AgentResult.builder()
            .success(true)
            .summary(result.translatedText())
            .data(Map.of("translatedText", result.translatedText(), ...))
            .build();
    }

    @Override
    public boolean healthCheck() { return true; }
}
```

### Step 5: 注册 AiServices Bean

```java
// LLMConfig.java
@Bean
public TranslationService translationService(ChatLanguageModel m) {
    return AiServices.builder(TranslationService.class).chatLanguageModel(m).build();
}
```

### Step 6: 添加编排触发关键词

```java
// GraphNodes.java → decomposeByKeyword()
if (combined.contains("翻译") || combined.contains("translate")) {
    return List.of(new SubTask("翻译处理", taskContent, "translation", 1, List.of()));
}
```

**SubTask 字段说明**：

| 字段 | 说明 |
|------|------|
| `name` | 子任务显示名称 |
| `content` | 子任务内容（传给 Agent） |
| `requiredCapability` | 能力标签，必须与 Agent 的 `capabilities` 之一匹配 |
| `order` | 执行序号（1-based） |
| `dependsOn` | 依赖的子任务 order 列表，空列表 = 可与其他无依赖子任务并行 |

**并行规则**：`executeAgentNode` 按 `dependsOn` 拓扑分层——同层无依赖的子任务通过 `CompletableFuture` 并发执行（wall clock ≈ max 而非 sum），跨层串行等待。

```java
// 示例：两个无依赖子任务 → 自动并行
new SubTask("工单分析", content, "ticket_process", 1, List.of()),      // deps=[]
new SubTask("知识检索", content, "rag_qa", 2, List.of())               // deps=[] → 与上面并行

// 示例：有依赖链 → 自动分层串行
new SubTask("数据提取", content, "data_report", 1, List.of()),         // Level 0
new SubTask("报告生成", content, "data_report", 2, List.of(1)),        // Level 1 → 等待 1
new SubTask("报告审查", content, "code_review", 3, List.of(2))         // Level 2 → 等待 2
```

### 验证

```bash
# 1. 编译
mvn compile

# 2. 启动后检查
curl http://localhost:8085/api/v2/tasks/agents | jq '.total'  # 应为 6

# 3. 提交测试任务
curl -X POST http://localhost:8085/api/v2/tasks/submit \
  -H "Content-Type: application/json" \
  -d '{"taskName":"翻译测试","taskContent":"Hello World","submitUserId":1,"tenantId":1}'
```

## 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8085 | 服务端口 |
| `spring.datasource.url` | jdbc:mysql://localhost:3306/agent_office | 数据库连接 |
| `spring.datasource.password` | `${DB_PASSWORD:123456}` | 数据库密码（支持环境变量） |
| `spring.security.user.name` | admin | 默认管理员用户名 |
| `spring.security.user.password` | `${ADMIN_PASSWORD:admin123}` | 默认管理员密码 |
| `llm.api-url` | https://api.deepseek.com/v1 | LLM API 地址 |
| `llm.api-key` | `${LLM_API_KEY:...}` | LLM API Key |
| `llm.model` | deepseek-v4-flash | 默认模型 |
| `llm.temperature` | 0.3 | 默认温度参数 |
| `llm.max-tokens` | 4096 | 默认最大 Token |
| `llm.timeout-seconds` | 300 | LLM 调用超时 |
| `llm.max-retries` | 3 | LLM 调用最大重试 |
| `jwt.expiration` | 3600000 | JWT 有效期（ms） |
| `jwt.refresh-expiration` | 604800000 | RefreshToken 有效期（ms） |
| `jasypt.encryptor.password` | `${JASYPT_PASSWORD:...}` | Jasypt 加密密钥 |

## 数据库表说明

| 表名 | 说明 |
|------|------|
| `agent_task` | 协同任务主表 |
| `agent_task_item` | 子任务执行记录 |
| `agent_info` | Agent 注册信息 |
| `agent_prompt_template` | Prompt 模板（支持版本管理） |
| `task_decompose_template` | 任务拆解模板 |
| `sys_user` | 用户表 |
| `sys_role` | 角色表 |
| `sys_permission` | 权限表 |
| `sys_role_permission` | 角色-权限关联 |
| `kb_document` | 知识库文档 |
| `llm_token_daily_stat` | Token 日消耗统计 |
| `approval_record` | 审批记录 |
| `agent_stat_report` | Agent 统计报告 |

## 线程池配置

| 线程池 | 核心线程 | 最大线程 | 队列 | 用途 |
|--------|---------|---------|------|------|
| `agentExecutor` | 10 | 50 | 200 | StateGraph 任务执行（虚拟线程） |
| `taskExecutor` | 5 | 20 | 100 | 通用异步任务 |
