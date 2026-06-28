package com.agentoffice.lc4j.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.agent.model.TranslationResult;
import com.agentoffice.lc4j.agent.service.TranslationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 翻译 Agent (LangChain4j 重写版) —— 支持多语言翻译，自动检测源语言。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationAgent implements BizAgent {

    private final AgentRegistry registry;
    private final TranslationService translationService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("translation_agent").agentName("多语言翻译Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("translation", "i18n", "multilingual"))
                        .inputFormats(List.of("text/plain", "text/markdown"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash")).build())
                .model("deepseek-v4-flash").temperature(0.3).maxTokens(4096).dailyTokenBudget(100000)
                .retryMax(3).timeoutSeconds(300).priority(15).maxConcurrency(10).build();
        registry.register(this, config);
        log.info("TranslationAgent (lc4j) registered");
    }

    @Override public String getAgentCode() { return "translation_agent"; }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("translation", "i18n", "multilingual"))
                .inputFormats(List.of("text/plain", "text/markdown"))
                .outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash")).build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            String sourceLang = (String) globalContext.getOrDefault("sourceLang", "auto");
            String targetLang = (String) globalContext.getOrDefault("targetLang", "Chinese");
            TranslationResult result = translationService.translate(sourceLang, targetLang, content);
            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder().success(true)
                    .summary(String.format("[%s→%s] %s", result.sourceLanguage(), result.targetLanguage(),
                            result.translatedText().length() > 100
                                    ? result.translatedText().substring(0, 100) + "..."
                                    : result.translatedText()))
                    .data(Map.of("sourceLanguage", result.sourceLanguage(),
                            "targetLanguage", result.targetLanguage(),
                            "translatedText", result.translatedText(),
                            "alternatives", result.alternatives(),
                            "confidence", result.confidence(),
                            "processedBy", getAgentCode()))
                    .costTimeMs(cost).build();
        } catch (Exception e) {
            String errMsg = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "unknown error");
            log.error("TranslationAgent failed: {}", errMsg, e);
            return AgentResult.builder().success(false)
                    .summary(errMsg)
                    .errorMsg(errMsg)
                    .costTimeMs(System.currentTimeMillis() - start).build();
        }
    }

    @Override public boolean healthCheck() { return true; }
}
