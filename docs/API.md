# API 参考 — Agent Office

## 认证

大部分 V1 接口需要 JWT Token。登录获取 Token：

```bash
curl -X POST http://localhost:8085/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

响应：

```json
{
  "code": 0,
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
    "username": "admin",
    "realName": "系统管理员"
  }
}
```

后续请求在 Header 中携带 Token：

```
Authorization: Bearer <accessToken>
```

> 💡 完整 API 文档请访问 **http://localhost:8085/doc.html** (Knife4j / OpenAPI 3)

---

## V2 StateGraph 端点（无需认证）

### 提交协同任务

```http
POST /api/v2/tasks/submit
Content-Type: application/json

{
  "taskName": "代码审查测试",
  "taskContent": "请审查 StringUtils.java 的代码质量和安全性",
  "submitUserId": 1,
  "tenantId": 1
}
```

响应示例：

```json
{
  "success": true,
  "traceId": "1534b6c6",
  "taskName": "代码审查测试",
  "coopMode": "SERIAL",
  "finalStatus": 1,
  "finalSummary": "## 代码审查测试 - 协同执行报告\n\n**协同模式**: SERIAL...",
  "agentResults": [
    {
      "agentCode": "code_review_agent",
      "taskName": "代码审查",
      "success": true,
      "summary": "评分: 85/100, 错误: 0, 警告: 2",
      "costTimeMs": 3394,
      "data": { "score": 85, "issues": [...] }
    }
  ]
}
```

### 查看 Agent 列表

```http
GET /api/v2/tasks/agents
```

响应示例：

```json
{
  "success": true,
  "total": 6,
  "enabled": 6,
  "agents": [
    {
      "code": "translation_agent",
      "capabilities": ["translation", "i18n", "multilingual"],
      "healthy": true
    }
  ]
}
```

---

## V1 任务端点（需要 JWT）

### 提交任务（写入数据库）

```http
POST /api/v1/tasks
Authorization: Bearer <token>
Content-Type: application/json

{
  "taskName": "工单处理测试",
  "taskContent": "请分析最近的工单分类"
}
```

### 查询任务列表

```http
GET /api/v1/tasks?page=1&size=10&status=1
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "taskNo": "AT1782609440593",
        "taskName": "工单处理测试",
        "coopMode": "PARALLEL",
        "taskStatus": 1,
        "traceId": "f17548e3",
        "resultSummary": "## 工单处理测试 - 协同执行报告...",
        "createTime": "2026-06-28T09:17:21"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 查询任务详情

```http
GET /api/v1/tasks/{taskId}
Authorization: Bearer <token>
```

### 取消任务

```http
DELETE /api/v1/tasks/{taskId}
Authorization: Bearer <token>
```

### 审批子任务

```http
POST /api/v1/tasks/{taskItemId}/approve
Authorization: Bearer <token>
```

```http
POST /api/v1/tasks/{taskItemId}/reject
Authorization: Bearer <token>
Content-Type: application/json

{ "reason": "内容不完整" }
```

---

## Auth 端点

### 登录

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "admin", "password": "admin123" }
```

### 刷新 Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{ "refreshToken": "eyJhbGciOiJIUzM4NCJ9..." }
```

---

## 可观测性端点

### 健康检查

```http
GET /actuator/health
```
→ `{"status":"UP"}`

### Prometheus 指标

```http
GET /actuator/prometheus
```

可用指标：
- `application_started_time_seconds`
- `agent_task_total{agent="code_review_agent"}`
- `agent_task_errors{agent="..."}`
- `agent_task_duration_seconds{agent="..."}`
- `llm_token_consumed{agent="...",model="deepseek-v4-flash"}`
- `http_server_requests_seconds`
- `jvm_memory_used_bytes`

---

## 功能验证页

| 页面 | URL | 说明 |
|------|-----|------|
| 功能验证 | http://localhost:8085 | 提交任务 → 观察 StateGraph 编排全链路（分解 → 执行 → 聚合） |
| API 文档 | http://localhost:8085/doc.html | Knife4j OpenAPI 3 完整文档 |
