package com.agentoffice.lc4j.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.agent.model.DocResult;
import com.agentoffice.lc4j.agent.service.DocumentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent implements BizAgent {

    private final AgentRegistry registry;
    private final DocumentService documentService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("document_agent").agentName("文档处理Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("doc_analysis", "doc_summary", "doc_rewrite"))
                        .inputFormats(List.of("text/plain", "text/markdown", "application/json"))
                        .outputFormats(List.of("application/json", "text/markdown"))
                        .supportedModels(List.of("deepseek-v4-flash")).build())
                .model("deepseek-v4-flash").temperature(0.4).maxTokens(4096).dailyTokenBudget(150000)
                .retryMax(3).timeoutSeconds(300).priority(15).maxConcurrency(5).build();
        registry.register(this, config);
        log.info("DocumentAgent (lc4j) registered");
    }

    @Override public String getAgentCode() { return "document_agent"; }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("doc_analysis", "doc_summary", "doc_rewrite"))
                .inputFormats(List.of("text/plain", "text/markdown", "application/json"))
                .outputFormats(List.of("application/json", "text/markdown"))
                .supportedModels(List.of("deepseek-v4-flash")).build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            String operation = (String) globalContext.getOrDefault("docOperation", "总结");
            DocResult result = documentService.process(operation, content);
            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder().success(true).summary("完成文档" + operation + "处理")
                    .data(Map.of("operation", result.operation(), "result", result.result(),
                            "keyPoints", result.keyPoints())).costTimeMs(cost).build();
        } catch (Exception e) {
            log.error("DocumentAgent failed", e);
            return AgentResult.builder().success(false).errorMsg(e.getMessage())
                    .costTimeMs(System.currentTimeMillis() - start).build();
        }
    }

    @Override public boolean healthCheck() { return true; }
}
