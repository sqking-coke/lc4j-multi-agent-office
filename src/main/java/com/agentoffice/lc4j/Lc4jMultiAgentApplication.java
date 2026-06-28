package com.agentoffice.lc4j;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基于任务分解 + 专用 Prompt 路由的 LLM 编排引擎。
 *
 * <p>设计思路：同一个 LLM，通过不同的 System Prompt + 温度配置 + 质检流程，
 * 模拟出多个专业 Agent 的行为，将复杂任务拆解后并行/串行执行，最后聚合为结构化报告。</p>
 *
 * <pre>
 * 核心组件:
 *   LLM 接入: ChatLanguageModel + 6 AiServices（接口化调用，JSON Schema 自动映射）
 *   编排引擎: StateGraph DAG（decompose → execute → aggregate）
 *   基础设施: JWT 安全、多租户隔离、Token 预算管理、Prometheus 可观测性
 * </pre>
 *
 * <p>诚实说明：当前所有 Agent 底层复用同一个 LLM。架构上的 Agent 接口与模型解耦，
 * 允许未来每个 Agent 路由到不同模型或服务。</p>
 */
@SpringBootApplication(scanBasePackages = "com.agentoffice")
@EnableScheduling
@MapperScan("com.agentoffice.mapper")
public class Lc4jMultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(Lc4jMultiAgentApplication.class, args);
    }
}
