package com.agentoffice.lc4j.config;

import com.agentoffice.lc4j.agent.service.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 统一配置 —— 替代手写版 LLMService (170行) + LLMProvider×3 (300行) + LLMCache (80行)。
 *
 * <pre>
 * 框架替代总览:
 *   LLMService (170行)              → ChatLanguageModel Builder (15行)    -91%
 *   OpenAIProvider (120行)          → langchain4j-open-ai 内置             -100%
 *   AnthropicProvider (100行)       → 同 OpenAiChatModel (兼容协议)        -100%
 *   LLMCache (80行)                 → ChatMemory (可选)                   -100%
 *   5 Agent LLM调用 (~350行)         → 5 AiServices 接口 (~100行)          -71%
 * </pre>
 */
@Slf4j
@Configuration
public class LLMConfig {

    @Value("${llm.api-url}")
    private String apiUrl;
    @Value("${llm.api-key}")
    private String apiKey;
    @Value("${llm.model:deepseek-v4-flash}")
    private String model;
    @Value("${llm.temperature:0.3}")
    private double temperature;
    @Value("${llm.max-tokens:4096}")
    private int maxTokens;
    @Value("${llm.timeout-seconds:300}")
    private long timeoutSeconds;
    @Value("${llm.max-retries:3}")
    private int maxRetries;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化 ChatLanguageModel: url={}, model={}", apiUrl, model);
        return OpenAiChatModel.builder()
                .baseUrl(apiUrl).apiKey(apiKey).modelName(model)
                .temperature(temperature).maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .build();
    }

    @Bean
    public TicketAnalysisService ticketAnalysisService(ChatLanguageModel m) {
        return AiServices.builder(TicketAnalysisService.class).chatLanguageModel(m).build();
    }

    @Bean
    public CodeReviewService codeReviewService(ChatLanguageModel m) {
        return AiServices.builder(CodeReviewService.class).chatLanguageModel(m).build();
    }

    @Bean
    public RagQaService ragQaService(ChatLanguageModel m) {
        return AiServices.builder(RagQaService.class).chatLanguageModel(m).build();
    }

    @Bean
    public DocumentService documentService(ChatLanguageModel m) {
        return AiServices.builder(DocumentService.class).chatLanguageModel(m).build();
    }

    @Bean
    public DataReportService dataReportService(ChatLanguageModel m) {
        return AiServices.builder(DataReportService.class).chatLanguageModel(m).build();
    }
}
