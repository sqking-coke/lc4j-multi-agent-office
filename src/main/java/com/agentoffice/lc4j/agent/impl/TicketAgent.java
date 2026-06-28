package com.agentoffice.lc4j.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.agent.model.TicketResult;
import com.agentoffice.lc4j.agent.service.TicketAnalysisService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工单处理 Agent (LangChain4j 重写版) — execute() 从 70行 → 25行 (-64%)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketAgent implements BizAgent {

    private final AgentRegistry registry;
    private final TicketAnalysisService ticketService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("ticket_agent").agentName("工单处理Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("ticket_process"))
                        .inputFormats(List.of("text/plain", "application/json"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash")).build())
                .model("deepseek-v4-flash").temperature(0.3).maxTokens(2048).dailyTokenBudget(100000)
                .retryMax(3).timeoutSeconds(300).priority(10).maxConcurrency(5).build();
        registry.register(this, config);
        log.info("TicketAgent (lc4j) registered");
    }

    @Override public String getAgentCode() { return "ticket_agent"; }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("ticket_process"))
                .inputFormats(List.of("text/plain", "application/json"))
                .outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash")).build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            String tenantName = (String) globalContext.getOrDefault("tenantName", "default");
            TicketResult result = ticketService.analyze(tenantName, "1-5", content);
            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder().success(true)
                    .summary(result.category() + " - 紧急度: " + result.urgency())
                    .data(Map.of("category", result.category(), "urgency", result.urgency(),
                            "entities", result.entities(), "suggestion", result.suggestion(),
                            "autoReply", result.autoReply(), "processedBy", getAgentCode()))
                    .costTimeMs(cost).build();
        } catch (Exception e) {
            log.error("TicketAgent failed", e);
            return AgentResult.builder().success(false).errorMsg(e.getMessage())
                    .costTimeMs(System.currentTimeMillis() - start).build();
        }
    }

    @Override public boolean healthCheck() { return true; }
}
