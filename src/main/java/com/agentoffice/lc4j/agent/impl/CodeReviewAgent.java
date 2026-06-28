package com.agentoffice.lc4j.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.agent.model.ReviewResult;
import com.agentoffice.lc4j.agent.service.CodeReviewService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewAgent implements BizAgent {

    private final AgentRegistry registry;
    private final CodeReviewService reviewService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("code_review_agent").agentName("代码审查Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("code_review", "bug_detection", "security_scan"))
                        .inputFormats(List.of("text/plain", "application/json"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash")).build())
                .model("deepseek-v4-flash").temperature(0.2).maxTokens(4096).dailyTokenBudget(200000)
                .retryMax(3).timeoutSeconds(300).priority(10).maxConcurrency(3).build();
        registry.register(this, config);
        log.info("CodeReviewAgent (lc4j) registered");
    }

    @Override public String getAgentCode() { return "code_review_agent"; }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("code_review", "bug_detection", "security_scan"))
                .inputFormats(List.of("text/plain", "application/json"))
                .outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash")).build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            String language = detectLanguage(content);
            ReviewResult result = reviewService.review(language, content);
            int errors = (int) result.issues().stream().filter(i -> "error".equals(i.severity())).count();
            int warns = (int) result.issues().stream().filter(i -> "warning".equals(i.severity())).count();
            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder().success(true)
                    .summary(String.format("评分: %d/100, 错误: %d, 警告: %d", result.score(), errors, warns))
                    .data(Map.of("score", result.score(), "language", language, "issues", result.issues(),
                            "summary", result.summary(), "reviewedAt", java.time.LocalDateTime.now().toString()))
                    .costTimeMs(cost).build();
        } catch (Exception e) {
            log.error("CodeReviewAgent failed", e);
            return AgentResult.builder().success(false).errorMsg(e.getMessage())
                    .costTimeMs(System.currentTimeMillis() - start).build();
        }
    }

    @Override public boolean healthCheck() { return true; }

    private String detectLanguage(String content) {
        if (content == null) return "unknown";
        if (content.contains("public class") || content.contains("import java")) return "java";
        if (content.contains("def ") || content.contains("import ")) return "python";
        if (content.contains("function ") || content.contains("const ")) return "javascript";
        return "unknown";
    }
}
