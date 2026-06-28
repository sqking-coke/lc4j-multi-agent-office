package com.agentoffice.lc4j.agent.service;

import com.agentoffice.lc4j.agent.model.RagQaResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * RAG 智能答疑 AiServices 接口 —— 替代手写版 RagQaAgent 中 LLM 调用（~65行）。
 */
public interface RagQaService {

    @SystemMessage(fromResource = "prompts/rag-qa.txt")
    RagQaResult answer(
        @V("kbContext") String kbContext,
        @UserMessage String question
    );
}
