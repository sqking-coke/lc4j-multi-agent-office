package com.agentoffice.lc4j.agent.service;

import com.agentoffice.lc4j.agent.model.DocResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 文档处理 AiServices 接口 —— 替代手写版 DocumentAgent 中 LLM 调用（~70行）。
 */
public interface DocumentService {

    @SystemMessage(fromResource = "prompts/document-process.txt")
    DocResult process(
        @V("operation") String operation,
        @UserMessage String document
    );
}
