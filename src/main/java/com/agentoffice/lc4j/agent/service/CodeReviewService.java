package com.agentoffice.lc4j.agent.service;

import com.agentoffice.lc4j.agent.model.ReviewResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码审查 AiServices 接口 —— 替代手写版 CodeReviewAgent 中 LLM 调用（~75行）。
 */
public interface CodeReviewService {

    @SystemMessage(fromResource = "prompts/code-review.txt")
    ReviewResult review(
        @V("language") String language,
        @UserMessage String code
    );
}
