package com.agentoffice.lc4j.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.agent.model.ReportResult;
import com.agentoffice.lc4j.agent.service.DataReportService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataReportAgent implements BizAgent {

    private final AgentRegistry registry;
    private final DataReportService reportService;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("data_report_agent").agentName("数据统计复盘Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("data_report", "data_summary", "trend_analysis"))
                        .inputFormats(List.of("text/plain", "application/json"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash")).build())
                .model("deepseek-v4-flash").temperature(0.2).maxTokens(4096).dailyTokenBudget(150000)
                .retryMax(3).timeoutSeconds(300).priority(20).maxConcurrency(5).build();
        registry.register(this, config);
        log.info("DataReportAgent (lc4j) registered");
    }

    @Override public String getAgentCode() { return "data_report_agent"; }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("data_report", "data_summary", "trend_analysis"))
                .inputFormats(List.of("text/plain", "application/json"))
                .outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash")).build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            String reportType = (String) globalContext.getOrDefault("reportType", "ad-hoc");
            String dataFields = (String) globalContext.getOrDefault("dataFields", "all");
            ReportResult result = reportService.generate(reportType, dataFields, content);
            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder().success(true).summary(result.summary())
                    .data(Map.of("summary", result.summary(), "metrics", result.metrics(),
                            "trends", result.trends(), "anomalies", result.anomalies(),
                            "recommendations", result.recommendations()))
                    .costTimeMs(cost).build();
        } catch (Exception e) {
            log.error("DataReportAgent failed", e);
            return AgentResult.builder().success(false).errorMsg(e.getMessage())
                    .costTimeMs(System.currentTimeMillis() - start).build();
        }
    }

    @Override public boolean healthCheck() { return true; }
}
