package com.agentoffice.lc4j.agent.service;

import com.agentoffice.lc4j.agent.model.TicketResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 工单分析 AiServices 接口 —— 替代手写版 TicketAgent 中 LLMService.completion() 调用（~70行）。
 */
public interface TicketAnalysisService {

    @SystemMessage(fromResource = "prompts/ticket-analyze.txt")
    TicketResult analyze(
        @V("tenantName") String tenantName,
        @V("priorityLevels") String priorityLevels,
        @UserMessage String ticketContent
    );
}
