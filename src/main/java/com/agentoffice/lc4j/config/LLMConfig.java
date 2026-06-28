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
 * LLM 统一配置。
 *
 * <p>当前所有 Agent 共享同一个 ChatLanguageModel Bean（默认 DeepSeek）。
 * 架构上每个 AiServices 接口独立注册为 Bean，允许未来按 Agent 类型注入不同模型：</p>
 * <pre>
 *   // 未来可改为：
 *   TranslationService  → DeepL API
 *   CodeReviewService   → Claude
 *   DataReportService   → DeepSeek
 * </pre>
 *
 * <p>相比手写 LLM 调用代码（JSON 解析 + 重试），AiServices 自动做 JSON Schema
 * 映射和反序列化，每个 Agent 的 LLM 调用代码从 ~70 行减到 ~5 行。</p>
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

    @Bean
    public TranslationService translationService(ChatLanguageModel m) {
        return AiServices.builder(TranslationService.class).chatLanguageModel(m).build();
    }
}
