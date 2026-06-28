package com.agentoffice.lc4j.agent.service;

import com.agentoffice.lc4j.agent.model.ReportResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 数据报告 AiServices 接口 —— 替代手写版 DataReportAgent 中 LLM 调用（~70行）。
 */
public interface DataReportService {

    @SystemMessage(fromResource = "prompts/data-report.txt")
    ReportResult generate(
        @V("reportType") String reportType,
        @V("dataFields") String dataFields,
        @UserMessage String data
    );
}
