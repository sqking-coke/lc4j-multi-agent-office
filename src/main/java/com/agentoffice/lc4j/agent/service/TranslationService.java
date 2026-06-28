package com.agentoffice.lc4j.agent.service;

import com.agentoffice.lc4j.agent.model.TranslationResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 翻译 AiServices 接口 —— 支持多语言翻译，自动检测源语言。
 */
public interface TranslationService {

    @SystemMessage(fromResource = "prompts/translate.txt")
    TranslationResult translate(
        @V("sourceLang") String sourceLang,
        @V("targetLang") String targetLang,
        @UserMessage String text
    );
}
