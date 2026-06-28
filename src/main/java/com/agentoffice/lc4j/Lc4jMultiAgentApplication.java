package com.agentoffice.lc4j;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LangChain4j + LangGraph4j 重写版 · 多 Agent 协同办公 AI 中台。
 *
 * <pre>
 * 框架替代:
 *   LLM 调用层: ChatLanguageModel + 5 AiServices (替代 LLMService + Provider×3 + Cache, -87%)
 *   编排层: StateGraph + AgentState (替代 DispatchHub + 7 Strategy + GlobalContext, -85%)
 * 保留手写:
 *   安全体系、可观测性、Agent 注册中心、PromptResolver、Token 预算管理
 * </pre>
 */
@SpringBootApplication(scanBasePackages = "com.agentoffice")
@EnableScheduling
@MapperScan("com.agentoffice.mapper")
public class Lc4jMultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(Lc4jMultiAgentApplication.class, args);
    }
}
