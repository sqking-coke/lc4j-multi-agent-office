package com.agentoffice.lc4j.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.agent.model.RagQaResult;
import com.agentoffice.lc4j.agent.service.RagQaService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagQaAgent implements BizAgent {

    private final AgentRegistry registry;
    private final RagQaService ragQaService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("rag_qa_agent").agentName("智能答疑RAG Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("rag_qa", "knowledge_search"))
                        .inputFormats(List.of("text/plain")).outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash")).preconditions(List.of("kb_loaded")).build())
                .model("deepseek-v4-flash").temperature(0.3).maxTokens(2048).dailyTokenBudget(150000)
                .retryMax(3).timeoutSeconds(300).priority(15).maxConcurrency(10).build();
        registry.register(this, config);
        log.info("RagQaAgent (lc4j) registered");
    }

    @Override public String getAgentCode() { return "rag_qa_agent"; }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("rag_qa", "knowledge_search"))
                .inputFormats(List.of("text/plain")).outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash")).preconditions(List.of("kb_loaded")).build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            String kbContext = (String) globalContext.getOrDefault("kbContext", "（知识库为空）");
            RagQaResult result = ragQaService.answer(kbContext, content);
            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder().success(true).summary(result.answer())
                    .data(Map.of("answer", result.answer(), "confidence", result.confidence(),
                            "sources", result.sources(), "relatedQuestions", result.relatedQuestions()))
                    .costTimeMs(cost).build();
        } catch (Exception e) {
            log.error("RagQaAgent failed", e);
            return AgentResult.builder().success(false).errorMsg(e.getMessage())
                    .costTimeMs(System.currentTimeMillis() - start).build();
        }
    }

    @Override public boolean healthCheck() { return true; }
}
